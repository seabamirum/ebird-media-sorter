package fun.seabird.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.lang3.Strings;

public final class MediaSortUtils 
{
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
	 * @return NULL if not an image or image is from an Apple or Google device--the
	 *         metadata otherwise
	 */
	public static JpegImageMetadata shouldAdjustExif(byte[] jpegImageFile) {
		ImageMetadata metadata;
		try {
			metadata = Imaging.getMetadata(jpegImageFile);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		if (!(metadata instanceof JpegImageMetadata))
			return null;

		JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

		// Don't adjust EXIF offset for mobile phones
		List<ImageMetadataItem> items = jpegMetadata.getItems();
		for (ImageMetadataItem item : items) {
			String itemStr = item.toString();
			if (Strings.CI.contains(itemStr, "Make") && Strings.CI.containsAny(itemStr, "Apple", "Google"))
				return null;
		}

		return jpegMetadata;
	}	
	
}
