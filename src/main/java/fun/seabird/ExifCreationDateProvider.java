package fun.seabird;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import com.google.common.io.Files;

public class ExifCreationDateProvider implements CreationDateProvider
{
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

	public LocalDateTime findCreationDate(File f,Long hrsOffset) throws IOException
	{
		String fileName = f.getName();
		String fileExt = Files.getFileExtension(fileName).toLowerCase();		
		
		boolean isImage = MediaSorterRunner.imageExtensions.contains(fileExt);
		boolean isAudio= MediaSorterRunner.audioExtensions.contains(fileExt);
		boolean isVideo = MediaSorterRunner.videoExtensions.contains(fileExt);
		
		Metadata metadata = null;		
		try(FileInputStream mediaStream = new FileInputStream(f))
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
		
		if (!isImage || hrsOffset == 0l || MediaSorterRunner.shouldAdjustExif(f) == null)
			return parseTime(dateTimeOrigStr,dtf);
		
		return parseTime(dateTimeOrigStr,dtf,hrsOffset);
	}
}
