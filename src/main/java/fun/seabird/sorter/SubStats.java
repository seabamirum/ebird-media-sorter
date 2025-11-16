package fun.seabird.sorter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
class SubStats 
{
	private static final DateTimeFormatter indexDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	private final LocalDateTime date;
	private final String subnational1Code;
	private final String county;
	private final String locName;

	private AtomicInteger numAssetsUploaded = new AtomicInteger(0);
	private AtomicInteger numAssetsLocal = new AtomicInteger(0);	

	public Integer getNumAssetsLocal() {
		return numAssetsLocal.get();
	}

	public int incNumAssetsLocal() {
		return numAssetsLocal.incrementAndGet();
	}
	
	public int incNumAssetsUploaded(int amount) {
		return numAssetsUploaded.addAndGet(amount);
	}

	public String getDate() {
		return date.format(indexDtf);
	}

	public int getNumAssetsUploaded() {
		return numAssetsUploaded.get();
	}
}
