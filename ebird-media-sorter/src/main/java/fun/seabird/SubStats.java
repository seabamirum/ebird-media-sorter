package fun.seabird;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SubStats 
{
	private static final DateTimeFormatter indexDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	private final LocalDateTime date;

	private int numAssetsUploaded = 0;
	private int numAssetsLocal = 0;		

	public SubStats(LocalDateTime date) {
		super();
		this.date = date;
	}

	public Integer getNumAssetsLocal() {
		return numAssetsLocal;
	}

	public void incNumAssetsLocal() {
		this.numAssetsLocal++;
	}
	
	public void incNumAssetsUploaded(int amount) {
		this.numAssetsUploaded += amount;
	}

	public String getDate() {
		return date.format(indexDtf);
	}

	public int getNumAssetsUploaded() {
		return numAssetsUploaded;
	}
}
