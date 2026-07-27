package com.CandleData.service.HistoricalData;

import com.CandleData.entity.HistoricalData.HistoricalData;
import com.CandleData.entity.HistoricalData.SyncTracker;
import com.CandleData.entity.stock.Stock;
import com.CandleData.repository.HistoricalData.HistoricalDataRepository;
import com.CandleData.repository.HistoricalData.SyncTrackerRepository;
import com.CandleData.service.kite.KiteService;
import com.CandleData.service.util.AppUtils;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoricalDataProcessor {

    private final KiteService kiteService;
    private final HistoricalDataRepository historicalRepository;
    private final SyncTrackerRepository trackerRepository;
    private final AppUtils appUtils;

    @Transactional
    public void processStockData(Stock stock, String interval, SyncTracker tracker) throws Exception, KiteException {
        //Initial Checks
    	 try {
    	        if (stock == null) {
    	            return;
    	        }
        SyncTracker currentTracker = (tracker != null) ? tracker : prepareNewTracker(stock, interval);

        if (appUtils.isAlreadySyncedToday(currentTracker)) {
            log.info("[SKIP] {} ({}) already synced.", stock.getTradingSymbol(), interval);
            return;
        }

        // Date Setup
        Date fromDate = appUtils.determineStartDate(currentTracker, interval);
        Date toDate = appUtils.determineToDate();
        if (fromDate.after(toDate)) {
            return;
        }

//        //Fetch Data
//        List<com.zerodhatech.models.HistoricalData> kiteData = fetchKiteData(stock, interval, fromDate, toDate);
//        if (kiteData.isEmpty()) return;
//
//        //Map & Save
//        List<HistoricalData> entities = kiteData.stream()
//                .map(d -> mapToEntity(stock, d))
//                .toList();
//
//        historicalRepository.saveBatch(interval, entities);
//
//        // Update Status
//        updateTracker(currentTracker, kiteData.get(kiteData.size() - 1).timeStamp);
//        log.info("[SUCCESS] {} ({}) - Records: {}", stock.getTradingSymbol(), interval, entities.size());
//    }
   
        
     // Fetch Data in Chunks
        Calendar chunkStart = Calendar.getInstance();
        chunkStart.setTime(fromDate);

        Calendar finalDate = Calendar.getInstance();
        finalDate.setTime(toDate);

        int chunkDays = appUtils.getChunkDays(interval);

        int totalRecords = 0;

        while (chunkStart.getTime().before(finalDate.getTime())) {

            Calendar chunkEnd = Calendar.getInstance();
            chunkEnd.setTime(chunkStart.getTime());
            chunkEnd.add(Calendar.DAY_OF_MONTH, chunkDays);

            if (chunkEnd.after(finalDate)) {
                chunkEnd.setTime(finalDate.getTime());
            }

            log.info("Fetching {} | {} -> {}",
                    interval,
                    chunkStart.getTime(),
                    chunkEnd.getTime());

            List<com.zerodhatech.models.HistoricalData> kiteData =
                    fetchKiteData(
                            stock,
                            interval,
                            chunkStart.getTime(),
                            chunkEnd.getTime()
                    );

            if (!kiteData.isEmpty()) {

                List<HistoricalData> entities = kiteData.stream()
                        .map(d -> mapToEntity(stock, d))
                        .toList();

                historicalRepository.saveBatch(interval, entities);

                totalRecords += entities.size();

                updateTracker(
                        currentTracker,
                        kiteData.get(kiteData.size() - 1).timeStamp
                );
            }

            chunkStart.setTime(chunkEnd.getTime());
            chunkStart.add(Calendar.SECOND, 1);
        }

        log.info("[SUCCESS] {} ({}) - Total Records: {}",
                stock.getTradingSymbol(),
                interval,
                totalRecords);
        }
    	 catch (Exception e) {

    	        log.error("====================================");
    	        log.error("FAILED STOCK : {}", stock.getTradingSymbol());
    	        log.error("TOKEN        : {}", stock.getInstrumentToken());
    	        log.error("INTERVAL     : {}", interval);
    	        log.error("ERROR        : {}", e.getMessage());
    	        log.error("====================================");

    	        SyncTracker failedTracker =
    	                (tracker != null)
    	                        ? tracker
    	                        : prepareNewTracker(stock, interval);

    	        failedTracker.setStatus("FAILED");
    	        failedTracker.setLastRunAt(java.time.LocalDateTime.now());

    	        trackerRepository.save(failedTracker);
    	}
   }
    

    private List<com.zerodhatech.models.HistoricalData> fetchKiteData(Stock stock, String interval, Date from, Date to) throws Exception, KiteException {
    	try {
    	    return kiteService.getKiteConnect()
    	            .getHistoricalData(
    	                    from,
    	                    to,
    	                    String.valueOf(stock.getInstrumentToken()),
    	                    interval,
    	                    false,
    	                    true
    	            ).dataArrayList;
    	} catch (KiteException e) {
    	    log.error("Kite Code    : {}", e.code);
    	    log.error("Kite Message : {}", e.message);

    	    }
    	 return Collections.emptyList();
    } 

    private HistoricalData mapToEntity(Stock stock, com.zerodhatech.models.HistoricalData d) {
        // 1. Kite format ko MySQL format mein convert karein
        // Kite input: "2026-01-23T09:15:00+0530"
        // MySQL output: "2026-01-23 09:15:00"
        String mysqlTimestamp = d.timeStamp
                .replace("T", " ")
                .split("\\+")[0];

        return HistoricalData.builder()
                .id(stock.getInstrumentToken() + "_" + mysqlTimestamp) // Unique ID
                .timeStamp(mysqlTimestamp) // Sahi date format
                .tradingSymbol(stock.getTradingSymbol())
                .instrumentToken(stock.getInstrumentToken())
                .open(d.open)
                .high(d.high)
                .low(d.low)
                .close(d.close)
                .volume(d.volume)
                .oi(d.oi)
                .build();
    }

    private SyncTracker prepareNewTracker(Stock stock, String interval) {
        return SyncTracker.builder()
                .tradingSymbol(stock.getTradingSymbol())
                .instrumentToken(stock.getInstrumentToken())
                .interval(interval).build();
    }

    private void updateTracker(SyncTracker tracker, String lastTime) {
        tracker.setLastFetchedTimestamp(lastTime);
        tracker.setStatus("SUCCESS");
        tracker.setLastRunAt(java.time.LocalDateTime.now());
        trackerRepository.save(tracker);
    }
}