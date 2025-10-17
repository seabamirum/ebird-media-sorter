package fun.seabird.sorter;

import java.nio.file.Path;

public class MediaSortCmd 
{	
	private Path mediaPath;
	private Long hrsOffset = 0l;
	
	private Path csvFile;
	private boolean reParseCsv=true;
	
	private boolean sepYear = false;	
	private boolean extractAudio = false;
	private boolean transcodeVideos = false;
	private boolean createSubDir = true;
	private boolean useSymbolicLinks = false;
	private FolderGroup folderGroup = FolderGroup.date;	

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
	public Path getCsvFile() {
		return csvFile;
	}
	public void setCsvFile(Path csvFile) {
		this.csvFile = csvFile;
	}	
	public boolean isCreateSubDir() {
		return createSubDir;
	}
	public void setCreateSubDir(boolean createSubDir) {
		this.createSubDir = createSubDir;
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
	public boolean isTranscodeVideos() {
		return transcodeVideos;
	}
	public void setTranscodeVideos(boolean transcodeVideos) {
		this.transcodeVideos = transcodeVideos;
	}
	public Path getMediaPath() {
		return mediaPath;
	}
	public void setMediaPath(Path mediaPath) {
		this.mediaPath = mediaPath;
	}
	public boolean isReParseCsv() {
		return reParseCsv;
	}
	public void setReParseCsv(boolean reParseCsv) {
		this.reParseCsv = reParseCsv;
	}
	public boolean isExtractAudio() {
		return extractAudio;
	}
	public void setExtractAudio(boolean extractAudio) {
		this.extractAudio = extractAudio;
	}
	
}
