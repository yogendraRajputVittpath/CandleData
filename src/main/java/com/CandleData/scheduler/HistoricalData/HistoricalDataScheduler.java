package com.CandleData.scheduler.HistoricalData;

import static com.CandleData.service.AppConstant.MINUTE;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.CandleData.entity.HistoricalData.SyncTracker;
import com.CandleData.entity.stock.Stock;
import com.CandleData.repository.HistoricalData.HistoricalDataRepository;
import com.CandleData.repository.HistoricalData.SyncTrackerRepository;
import com.CandleData.repository.stock.StockRepository;
import com.CandleData.service.ErrorCodes;
import com.CandleData.service.HistoricalData.HistoricalDataProcessor;
import com.CandleData.service.HistoricalData.HistoricalSyncEmailService;
import com.CandleData.service.Logs.LogService;
import com.CandleData.service.kite.KiteService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Component
@Slf4j
@RequiredArgsConstructor
public class HistoricalDataScheduler {

    private final StockRepository stockRepository;
    private final HistoricalDataProcessor processor;
    private final HistoricalDataRepository historicalRepository;
    private final KiteService kiteService;
    private final SyncTrackerRepository trackerRepository;
    private final LogService logService;
    private final HistoricalSyncEmailService historicalSyncEmailService;
    
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void runDailySync() throws KiteException {
    	
    	Date startTime = new Date();
        int totalFailures = 0;
        
        if (!isTokenValid()) {           
            log.error("Kite token missing. Login required.");
            logService.logError(
                    ErrorCodes.ERR_KITE_TOKEN,"Access token is null","Scheduler start");
            return;
        }

        log.info("Historical data sync started at {}", new Date());
        
        List<Stock> stocks = stockRepository.findAll();
        log.info("Total Stocks Found: {}", stocks.size());

        String[] intervals = {MINUTE,"5minute", "15minute", "60minute", "day", "week"};
        //String monthYear = new SimpleDateFormat("MMM_yyyy").format(new Date()).toUpperCase();

        for (String interval : intervals) {
            log.info("Processing Interval: [{}]", interval);
           
            List<SyncTracker> tracker = trackerRepository.findByInterval(interval);         
            Map<Long, SyncTracker> map = createTrackerMapfromList(tracker);
            
            for (int i = 0; i < stocks.size(); i++) {
                Stock stock = stocks.get(i);
                try {
                    if (i % 50 == 0) log.info("Progress: {}/{} stocks done for {}", i, stocks.size(), interval);
                    
                    processor.processStockData(stock, interval, map.get(stock.getInstrumentToken()));
                    Thread.sleep(350); 
                    
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logService.logError(  ErrorCodes.ERR_THREAD_INTERRUPTED,
                            ie.getMessage(),"Scheduler | " + stock.getTradingSymbol());
                    
                } catch (Exception e) {
                    logService.logError( ErrorCodes.ERR_GENERIC,
                            e.getMessage(),"Scheduler | symbol=" + stock.getTradingSymbol());

                	 try {
                	        SyncTracker failedTracker = map.get(stock.getInstrumentToken());
                	        if (failedTracker != null) {
                	            failedTracker.setStatus("FAILED");
                	            failedTracker.setLastRunAt(java.time.LocalDateTime.now());
                	            trackerRepository.save(failedTracker);
                	        }
                	    } catch (Exception ex) {
                            logService.logError( ErrorCodes.ERR_DB_SAVE,
                                    ex.getMessage(),"Updating FAILED tracker");
                            }
                      }
                }
          }
        Date endTime = new Date();
        log.info("Historical data sync completed at {}", new Date());
     
        //EMAIL
        try {
            historicalSyncEmailService.sendHistoricalSyncCompletionMail(
            		"shubangipachauri7@gmail.com",
                    //"info@vittyaan.com",
                    stocks.stream()
                          .map(Stock::getTradingSymbol)
                          .toList(),
                    List.of(intervals),
                    startTime,
                    endTime,
                    stocks.size(),
                    totalFailures
            );
        } catch (Exception e) {
            log.error("Failed to send historical sync completion email", e);
        }
    }
    
    private Map<Long, SyncTracker> createTrackerMapfromList(List<SyncTracker> trackerList) {
    	if (trackerList == null || trackerList.isEmpty()) {
            return new HashMap<>();
        }
    	return trackerList.stream()
                .collect(Collectors.toMap(
                		SyncTracker::getInstrumentToken, 
                    tracker -> tracker,                                      
                    (existing, replacement) -> existing                      
                ));
    }
    
	private boolean isTokenValid() {
        try {
            return kiteService.getKiteConnect().getAccessToken() != null;
        } catch (Exception e) {
            return false;
        }
    }
}