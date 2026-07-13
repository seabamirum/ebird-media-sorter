package dev.seabird.app;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Holds statistics for a single eBird checklist submission.
 */
@Getter
@RequiredArgsConstructor
class SubStats 
{
    private static final DateTimeFormatter indexDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    private final LocalDateTime date;
    private final String subnational1Code;
    private final String county;
    private final String locName;

    private final AtomicInteger numAssetsUploaded = new AtomicInteger(0);
    private final AtomicInteger numAssetsLocal = new AtomicInteger(0);	

    /** Resets the local asset count (used when re-processing). */
    public void reset()
    {
        numAssetsLocal.set(0);
    }

    /**
     * Returns the number of assets that exist locally.
     * @return number of local assets
     */
    public Integer getNumAssetsLocal() {
        return numAssetsLocal.get();
    }

    /**
     * Increments and returns the local asset count.
     * @return the new local asset count
     */
    public int incNumAssetsLocal() {
        return numAssetsLocal.incrementAndGet();
    }
    
    /**
     * Increments the uploaded asset count.
     * 
     * @param amount number of assets to add
     * @return the new uploaded asset count
     */
    public int incNumAssetsUploaded(int amount) {
        return numAssetsUploaded.addAndGet(amount);
    }

    /**
     * Returns the checklist date formatted for the index CSV.
     * @return formatted date string
     */
    public String getDate() {
        return date.format(indexDtf);
    }

    /**
     * Returns the number of assets uploaded to eBird.
     * @return number of uploaded assets
     */
    public int getNumAssetsUploaded() {
        return numAssetsUploaded.get();
    }
}
