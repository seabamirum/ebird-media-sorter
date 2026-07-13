package fun.seabird.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Date;

public class FileModifiedCreationDateProvider implements CreationDateProvider 
{
	/** 
	 * A constant representing January 1, 1900 at midnight.
	 * Used as a fallback/default date for images with missing or invalid timestamps.
	 */
	final LocalDateTime nineteenHundred = parseTime("1900:01:01 00:00:00",imageDtf);	

	@Override
	public LocalDateTime findCreationDate(Path f,long hrsOffset) throws IOException
	{
		Long epoch = Files.getLastModifiedTime(f).toMillis();
		if (epoch == 0L)
			return nineteenHundred;
		
		return parseTime(new Date(epoch).toString(),timezoneDtf);		
	}
}
