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
	private boolean createParentDir = true;
	private boolean useSymbolicLinks = false;
	private FolderGroup folderGroup = FolderGroup.date;
	
	enum FolderGroup {location,date}
	
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
	public boolean isCreateParentDir() {
		return createParentDir;
	}
	public void setCreateParentDir(boolean createParentDir) {
		this.createParentDir = createParentDir;
	}
	public boolean isUseSymbolicLinks() {
		return useSymbolicLinks;
	}
	public void setUseSymbolicLinks(boolean useSymbolicLinks) {
		this.useSymbolicLinks = useSymbolicLinks;
	}
	public FolderGroup getFolderGroup() {
		return folderGroup;
	}
	public void setFolderGroup(FolderGroup folderGroup) {
		this.folderGroup = folderGroup;
	}
	
}
