package fun.seabird;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FakeCreationDateProvider implements CreationDateProvider 
{
	static final DateTimeFormatter recForgeDtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	
	final LocalDateTime nineteenHundred = parseTime("1900:01:01 00:00:00",imageDtf);
	
	@Override
	public LocalDateTime findCreationDate(File f,Long hrsOffset)
	{
		return nineteenHundred;
	}
}
