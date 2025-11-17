package fun.seabird.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.media.QuickTimeMediaDirectory;
import com.drew.metadata.mp4.Mp4Directory;

import fun.seabird.util.MediaSortUtils;

public class ExifCreationDateProvider implements CreationDateProvider
{
	private static final Logger log = LoggerFactory.getLogger(ExifCreationDateProvider.class);
	
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");	
	
	private static Directory getVideoDirectory(Metadata metadata, String ext) {
	    return switch (ext) {
	        case "mp4" -> metadata.getFirstDirectoryOfType(Mp4Directory.class);
	        case "mov" -> metadata.getFirstDirectoryOfType(QuickTimeMediaDirectory.class);
	        case "avi" -> metadata.getFirstDirectoryOfType(AviDirectory.class);
	        default -> null;
	    };
	}

	private static int getVideoTag(String ext) {
	    return switch (ext) {
	        case "mp4" -> Mp4Directory.TAG_CREATION_TIME;
	        case "mov" -> QuickTimeMediaDirectory.TAG_CREATION_TIME;
	        case "avi" -> AviDirectory.TAG_DATETIME_ORIGINAL;
	        default -> throw new IllegalArgumentException("Unsupported video extension: " + ext);
	    };
	}
	
	@Override
	public LocalDateTime findCreationDate(Path f, long hrsOffset) throws IOException {
	    String fileName = f.getFileName().toString();
	    String fileExt = MediaSortUtils.getFileExtension(fileName).toLowerCase();

	    boolean isImage = MediaSortUtils.imageExtensions.contains(fileExt);
	    boolean isVideo = MediaSortUtils.videoExtensions.contains(fileExt);

	    if (!isImage && !isVideo) {
	        return null;
	    }

	    Metadata metadata = null;
	    try (InputStream stream = Files.newInputStream(f)) {
	        try {
	            metadata = ImageMetadataReader.readMetadata(stream);
	        } catch (ImageProcessingException | IOException e) {
	            log.warn("Error reading metadata from {}", fileName, e);
	        }
	    }

	    if (metadata == null) {
	        return null;
	    }

	    DateTimeFormatter dtf = isImage ? imageDtf : timezoneDtf;
	    Directory directory = isImage ? metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class) : getVideoDirectory(metadata, fileExt);

	    if (directory == null)
	        return null;

	    int tag = isImage ? ExifDirectoryBase.TAG_DATETIME_ORIGINAL : getVideoTag(fileExt);
	    String dateTimeStr = directory.getString(tag);

	    return parseTime(dateTimeStr, dtf, hrsOffset);
	}
	
}
