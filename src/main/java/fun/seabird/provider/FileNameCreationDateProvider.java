package fun.seabird.provider;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

public class FileNameCreationDateProvider implements CreationDateProvider 
{
	private static final DateTimeFormatter recForgeDtf1 = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
	private static final DateTimeFormatter recForgeDtf2 = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
	private static final DateTimeFormatter merlinOldDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");
	private static final DateTimeFormatter merlinNewDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH_mm");
	
	@Override
	/**
	 * This method attempts to find the creation date of the given file by parsing the date and time from the file name.
	 * It supports three file name formats:
	 *   - "yyyyMMdd_HHmm" or "yyyyMMdd-HHmm" for files created by RecForge on Android devices
	 *   - "yyyy-MM-dd HHmm" for files created by Merlin
	 *   - "yyyy-MM-dd HH_mm" for files created by a newer version of Merlin
	 *
	 * If the file name does not match any of these formats or if the date and time information is missing, this method
	 * returns null.
	 *
	 * @param f the file to find the creation date for
	 * @param hrsOffset the offset in hours to add or subtract from the creation date (can be null)
	 * @return the creation date of the file, or null if it cannot be determined
	 */
	public LocalDateTime findCreationDate(Path f,Long hrsOffset)
	{
		String fileName = f.getFileName().toString();
		String dateTimeOrigStr = StringUtils.left(fileName,16);
		
		if (!Strings.CS.startsWithAny(dateTimeOrigStr,"1","2") || StringUtils.containsNone(dateTimeOrigStr,'-','_'))
			return null;
		
		if (StringUtils.countMatches(dateTimeOrigStr,"-") == 2)
		{
			if (StringUtils.countMatches(dateTimeOrigStr,"_") == 1)
				return parseTime(dateTimeOrigStr,merlinNewDtf);
			
			return parseTime(StringUtils.left(fileName,15),merlinOldDtf);
		}
		
		return dateTimeOrigStr.contains("_") ? parseTime(StringUtils.left(fileName,13),recForgeDtf1) : parseTime(StringUtils.left(fileName,13),recForgeDtf2);
	}
}
