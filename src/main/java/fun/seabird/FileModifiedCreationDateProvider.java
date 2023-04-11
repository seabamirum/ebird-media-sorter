package fun.seabird;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Date;

public class FileModifiedCreationDateProvider implements CreationDateProvider 
{
	final LocalDateTime nineteenHundred = parseTime("1900:01:01 00:00:00",imageDtf);	

	@Override
	public LocalDateTime findCreationDate(Path f,Long hrsOffset) throws IOException
	{
		Long epoch = Files.getLastModifiedTime(f).toMillis();
		if (epoch == 0L)
			return nineteenHundred;
		
		return parseTime(new Date(epoch).toString(),timezoneDtf);		
	}
}
