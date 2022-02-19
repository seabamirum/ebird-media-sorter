package fun.seabird;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.StringUtils;

public class FileNameCreationDateProvider implements CreationDateProvider 
{
	static final DateTimeFormatter recForgeDtf1 = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
	static final DateTimeFormatter recForgeDtf2 = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
	static final DateTimeFormatter merlinDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");	
	
	@Override
	public LocalDateTime findCreationDate(File f,Long hrsOffset)
	{
		String fileName = f.getName();
		String dateTimeOrigStr = StringUtils.left(fileName,13);
		
		if (StringUtils.containsNone(dateTimeOrigStr,'-','_'))
			return null;
		
		if (StringUtils.countMatches(dateTimeOrigStr,"-") == 2)
			return parseTime(StringUtils.left(fileName,15),merlinDtf);
		
		return dateTimeOrigStr.contains("_") ? parseTime(dateTimeOrigStr,recForgeDtf1) : parseTime(dateTimeOrigStr,recForgeDtf2);
	}
}
