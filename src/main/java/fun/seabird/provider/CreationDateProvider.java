package fun.seabird.provider;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public interface CreationDateProvider 
{
	static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	static final DateTimeFormatter timezoneDtf = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");
	
	default LocalDateTime parseTime(String dateTimeOrigStr,DateTimeFormatter dtf)
	{
		try
		{
			return dateTimeOrigStr == null ? null : LocalDateTime.parse(dateTimeOrigStr,dtf);
		}
		catch (DateTimeParseException dtpe) 
		{
			System.err.println("Can't parse LocalDateTime for " + dateTimeOrigStr);
			return null;
		}
	}
	
	default LocalDateTime parseTime(String dateTimeOrigStr,DateTimeFormatter dtf,long hrsOffset)
	{
		LocalDateTime mediaTime = parseTime(dateTimeOrigStr,dtf);
		return mediaTime == null ? null : (hrsOffset == 0l ? mediaTime : mediaTime.plusHours(hrsOffset));
	}
	
	LocalDateTime findCreationDate(Path f,long hrsOffset) throws IOException;
}
