package com.CandleData.service.util;

import static com.CandleData.service.AppConstant.DAY;
import static com.CandleData.service.AppConstant.FIFTEEN_MINUTE;
import static com.CandleData.service.AppConstant.FIVE_MINUTE;
import static com.CandleData.service.AppConstant.MARKET_OPEN_TIME;
import static com.CandleData.service.AppConstant.MINUTE;
import static com.CandleData.service.AppConstant.SIXTY_MINUTE;
import static com.CandleData.service.AppConstant.WEEK;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.CandleData.entity.HistoricalData.SyncTracker;

@Service
public class AppUtils {
	
    @Value("${historical.default.start-date}")
    private String defaultStartDate;

    public Date determineToDate() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(15, 30))) {
            
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DATE, -1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 15);
            cal.set(java.util.Calendar.MINUTE, 30);
            return cal.getTime();
        } else {
            return new Date();
        }
    }
    
	public Date determineStartDate(SyncTracker tracker, String interval) throws Exception {
	        
	        DateTimeFormatter kiteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
	        
	        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	        
	       
	        if (tracker.getLastFetchedTimestamp() != null && !tracker.getLastFetchedTimestamp().isEmpty()) {
	            try {  
	                OffsetDateTime odt = OffsetDateTime.parse(tracker.getLastFetchedTimestamp(), kiteFormatter);
	                return Date.from(odt.toInstant().plusSeconds(1));
	            } catch (Exception e) {
	                Date lastDate = dbFormat.parse(tracker.getLastFetchedTimestamp());
	                return new Date(lastDate.getTime() + 1000);
	            }
	        }
	        // Agar ekdum naya stock hai,
	        String start;
	        interval = interval.toLowerCase();
	        switch (interval) {
	        	case MINUTE -> start = LocalDate.now().minusDays(60) + MARKET_OPEN_TIME;
	            case FIVE_MINUTE -> start = LocalDate.now().minusDays(100) + MARKET_OPEN_TIME;
	            case FIFTEEN_MINUTE -> start = LocalDate.now().minusDays(200) + MARKET_OPEN_TIME;
	            case SIXTY_MINUTE-> start = LocalDate.now().minusDays(400) + MARKET_OPEN_TIME;
	            case DAY -> start = LocalDate.now().minusYears(5) + MARKET_OPEN_TIME;   
	            case WEEK -> start = LocalDate.now().minusYears(5) + MARKET_OPEN_TIME;
	            default ->   start = defaultStartDate; 
	        }
	        return dbFormat.parse(start);
	    }
	
	   public boolean isAlreadySyncedToday(SyncTracker tracker) {
	        return tracker.getLastRunAt() != null && 
	               tracker.getLastRunAt().toLocalDate().equals(LocalDate.now()) && 
	               "SUCCESS".equals(tracker.getStatus());
	    }
	   
	   public int getChunkDays(String interval) {

		    return switch (interval.toLowerCase()) {

		        case MINUTE -> 60;

		        case FIVE_MINUTE -> 100;

		        case FIFTEEN_MINUTE -> 100;

		        case SIXTY_MINUTE -> 100;

		        case DAY, WEEK -> 3650;

		        default -> 30;
		    };
		}
	   
}
