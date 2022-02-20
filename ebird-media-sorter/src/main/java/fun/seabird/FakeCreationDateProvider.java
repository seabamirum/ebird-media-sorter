package fun.seabird;

import java.io.File;
import java.time.LocalDateTime;

public class FakeCreationDateProvider implements CreationDateProvider 
{
	final LocalDateTime nineteenHundred = parseTime("1900:01:01 00:00:00",imageDtf);
	
	@Override
	public LocalDateTime findCreationDate(File f,Long hrsOffset)
	{
		return nineteenHundred;
	}
}
