package fun.seabird.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MediaSortUtils 
{
	private static final Logger log = LoggerFactory.getLogger(MediaSortUtils.class);
	
	private MediaSortUtils () {};
	
	public static final String OUTPUT_FOLDER_NAME = "ebird";
	
	public static final Set<String> audioExtensions = Set.of("wav", "mp3", "m4a");
	public static final Set<String> videoExtensions = Set.of("mov", "m4v", "mp4", "avi");
	public static final Set<String> imageExtensions = Set.of("jpg", "jpeg", "png", "crx", "crw", "cr2", "cr3", "crm", "arw", "nef", "orf", "raf");
	public static final Set<String> mediaExtensions = Stream.of(imageExtensions,audioExtensions,videoExtensions).flatMap(Set::stream).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
	
	public static String getBaseName(String fileName) {
	    int dotIndex = fileName.lastIndexOf('.');
	    return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
	}
	
	public static String getFileExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
	}
	
	public static String getFileExtension(Path file) {
		return getFileExtension(file.getFileName().toString());
	}
	
	/**
	 * @param jpegImageFile
	 * @return NULL if not an image, the metadata otherwise
	 */
	public static JpegImageMetadata jpegImageMetadata(byte[] jpegImageFile) {
	    try {
	        return Imaging.getMetadata(jpegImageFile) instanceof JpegImageMetadata meta ? meta : null;
	    } catch (IOException e) {
	        log.warn("Error reading JPEG metadata", e);
	        return null;
	    }
	}
	
}
