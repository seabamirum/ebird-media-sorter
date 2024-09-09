package fun.seabird.util;

import java.util.Set;

public final class MediaSortConstants 
{
	private MediaSortConstants () {};
	
	public static final String OUTPUT_FOLDER_NAME = "ebird";
	
	public static final Set<String> audioExtensions = Set.of("wav", "mp3", "m4a");
	public static final Set<String> videoExtensions = Set.of("mov", "m4v", "mp4");
	public static final Set<String> imageExtensions = Set.of("jpg", "jpeg", "png", "crx", "crw", "cr2", "cr3", "crm",
			"arw", "nef", "orf", "raf");
}
