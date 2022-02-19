package fun.seabird;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Date;

public class FileModifiedCreationDateProvider implements CreationDateProvider {

	@Override
	public LocalDateTime findCreationDate(File f,Long hrsOffset)
	{
		return parseTime(new Date(f.lastModified()).toString(),timezoneDtf);		
	}
}
