package fun.seabird.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.media.QuickTimeMediaDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.wav.WavDirectory;

import fun.seabird.util.MediaSortUtils;

public class ExifCreationDateProvider implements CreationDateProvider
{
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

	/**
	 * Attempts to determine the creation date of a media file (image, audio, or video) by reading its metadata.
	 * The method supports various file extensions and uses specific logic based on the file type to locate the
	 * correct metadata indicating the file's creation date. For images, audio, and video files, different metadata
	 * directories are queried. The method also allows for adjusting the returned date based on a specified hour offset,
	 * which is particularly useful for correcting timezone differences in the timestamps of images.
	 *
	 * @param f the {@link Path} to the media file for which the creation date is being sought.
	 * @param hrsOffset the offset in hours to be applied to the creation date. This is useful for adjusting
	 *                  the creation timestamp of the file to account for timezone differences or incorrect
	 *                  camera settings. A value of 0 indicates no adjustment is to be made.
	 * @return a {@link LocalDateTime} representing the creation date of the file, adjusted by the specified
	 *         hour offset if applicable. Returns {@code null} if the creation date could not be determined
	 *         or if errors occur during metadata reading that prevent determining the date.
	 * @throws IOException if an error occurs while opening the file or reading its contents.
	 * 
	 * <p>Notes:</p>
	 * <ul>
	 *     <li>The method reads metadata using {@link ImageMetadataReader} and can handle exceptions like
	 *     {@link ImageProcessingException} and {@link StringIndexOutOfBoundsException} by logging them
	 *     to standard error without throwing them further.</li>
	 *     <li>Supports a range of media types including images, audio, and videos, with specific handling
	 *     based on file extensions.</li>
	 *     <li>If the media file's metadata does not contain a creation date or if the file type is unsupported,
	 *     the method may return {@code null}.</li>
	 *     <li>The accuracy of the returned creation date depends on the presence and correctness of metadata
	 *     in the media file.</li>
	 * </ul>
	 */
	@Override
	public LocalDateTime findCreationDate(Path f,Long hrsOffset) throws IOException
	{
		String fileName = f.getFileName().toString();
		String fileExt = MediaSortUtils.getFileExtension(fileName).toLowerCase();		
		
		boolean isImage = MediaSortUtils.imageExtensions.contains(fileExt);
		boolean isAudio= MediaSortUtils.audioExtensions.contains(fileExt);
		boolean isVideo = MediaSortUtils.videoExtensions.contains(fileExt);
		
		Metadata metadata = null;		
		try(InputStream mediaStream = Files.newInputStream(f))
		{
			try {
				metadata = ImageMetadataReader.readMetadata(mediaStream);		
			}
			catch(ImageProcessingException ipe)
			{
				System.err.println("Error reading " + fileName + ": " + ipe);
			}
			catch(StringIndexOutOfBoundsException sobe)
			{
				System.err.println("Error reading " + fileName + ": " + sobe);
			}
		}
		
		Directory directory = null; 
		String dateTimeOrigStr = null;
		DateTimeFormatter dtf = isImage ? imageDtf : timezoneDtf;
		int creationTimeTag = ExifDirectoryBase.TAG_DATETIME_ORIGINAL;
		if (metadata != null)
		{
			if (isImage)
				directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			else if (isVideo)
			{
				if (fileExt.equals("mp4"))
					directory = metadata.getFirstDirectoryOfType(Mp4Directory.class);
				else if (fileExt.equals("mov"))
					directory = metadata.getFirstDirectoryOfType(QuickTimeMediaDirectory.class);				
			}
			else if (isAudio && fileExt.equals("wav"))
				directory = metadata.getFirstDirectoryOfType(WavDirectory.class);
			
			if (directory != null)
			{
		        if (isVideo)
		        	if (fileExt.equals("mp4"))
			        	creationTimeTag = Mp4Directory.TAG_CREATION_TIME;
					else if (fileExt.equals("mov"))
						creationTimeTag = QuickTimeMediaDirectory.TAG_CREATION_TIME;
		        else if (isAudio)
		        	creationTimeTag = WavDirectory.TAG_DATE_CREATED;	
		        
	        	dateTimeOrigStr = directory.getString(creationTimeTag);
			}
		}
		
		if (hrsOffset == 0l || (isImage && MediaSortUtils.shouldAdjustExif(Files.readAllBytes(f)) == null))
			return parseTime(dateTimeOrigStr,dtf);
		
		return parseTime(dateTimeOrigStr,dtf,hrsOffset);
	}
}
