package fun.seabird;

import java.io.File;

import javax.swing.JProgressBar;

public class MediaSortCmd 
{
	private transient JProgressBar pb;
	
	private String mediaPath;
	private Long hrsOffset = 0l;
	private File csvFile;
	private boolean sepYear = false;	
	
	public Long getHrsOffset() {
		return hrsOffset;
	}
	public void setHrsOffset(Long hrsOffset) {
		this.hrsOffset = hrsOffset;
	}	
	public boolean isSepYear() {
		return sepYear;
	}
	public void setSepYear(boolean sepYear) {
		this.sepYear = sepYear;
	}
	public String getMediaPath() {
		return mediaPath;
	}
	public void setMediaPath(String mediaPath) {
		this.mediaPath = mediaPath;
	}
	public File getCsvFile() {
		return csvFile;
	}
	public void setCsvFile(File csvFile) {
		this.csvFile = csvFile;
	}
	public JProgressBar getPb() {
		return pb;
	}
	public void setPb(JProgressBar pb) {
		this.pb = pb;
	}
	
}
