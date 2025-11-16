package fun.seabird.sorter;

import java.nio.file.Path;

import lombok.Data;

@Data
public class MediaSortCmd 
{	
	private Path mediaPath;
	private long hrsOffset = 0l;
	
	private Path csvFile;
	private boolean reParseCsv=true;
	
	private boolean sepYear = false;	
	private boolean extractAudio = false;
	private boolean transcodeVideos = false;
	private boolean createSubDir = true;
	private boolean useSymbolicLinks = false;
	private FolderGroup folderGroup = FolderGroup.date;	
}
