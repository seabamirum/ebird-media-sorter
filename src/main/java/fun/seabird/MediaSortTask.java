package fun.seabird;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;

import fun.seabird.EbirdCsvParser.ParseMode;
import fun.seabird.EbirdCsvParser.PreSort;
import fun.seabird.MediaSortCmd.FolderGroup;
import javafx.concurrent.Task;

public class MediaSortTask extends Task<Path> {
	private static final Logger logger = LoggerFactory.getLogger(MediaSortTask.class);

	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final DateTimeFormatter folderDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public static final Set<String> audioExtensions = Set.of("wav", "mp3", "m4a");
	public static final Set<String> videoExtensions = Set.of("mov", "m4v", "mp4");
	public static final Set<String> imageExtensions = Set.of("jpg", "jpeg", "png", "crx", "crw", "cr2", "cr3", "crm",
			"arw", "nef", "orf", "raf");

	public static final String OUTPUT_FOLDER_NAME = "ebird";

	private static final long MAX_ML_UPLOAD_SIZE_VIDEO = 250l;
	private static final String TRANSCODED_VIDEO_SUFFIX = "_s";

	private static final String[] invalidChars = new String[] { " ", ":", ",", ".", "/", "\\", ">", "<" };
	private static final String[] validChars = new String[] { "-", "--", "-", "-", "-", "-", "-", "-" };

	private static final List<CreationDateProvider> creationDateProviders = List.of(new ExifCreationDateProvider(),
			new FileNameCreationDateProvider(), new FileModifiedCreationDateProvider());
	
	//eBird CSV fields
	private static final ReadWriteLock rangeMapLock = new ReentrantReadWriteLock();
	private static final RangeMap<LocalDateTime, String> rangeMap = TreeRangeMap.create();
	private static final Map<String, SubStats> checklistStatsMap = new ConcurrentSkipListMap<>();
	
	private final MediaSortCmd msc;

	private transient Process process;	

	public MediaSortTask(MediaSortCmd msc) {
		this.msc = msc;
	}

	/**
	 * Parses a CSV record and updates the checklist statistics map and range map
	 * with relevant information.
	 * 
	 * @param record The CSV record to be parsed.
	 */
	private void parseCsvLine(EbirdCsvRow row) 
	{
		int duration = row.getDuration();
		if (duration <= 0)
			return;
		
		if (row.getTime() == null)
			return;

		String subId = row.getSubId();
		if (!checklistStatsMap.containsKey(subId)) {			

			LocalDateTime subBeginTime = row.getDate().atTime(row.getTime());
			LocalDateTime subEndTime = subBeginTime.plusMinutes(duration);

			checklistStatsMap.putIfAbsent(subId, new SubStats(subBeginTime,row.getSubnat1Code(),row.getSubnat2Name(),row.getLocName()));

			rangeMapLock.writeLock().lock();
			try {
				rangeMap.put(Range.closed(subBeginTime, subEndTime), subId);
			} finally {
				rangeMapLock.writeLock().unlock();
			}
		}

		if (!row.getAssetIds().isEmpty())
			checklistStatsMap.get(subId).incNumAssetsUploaded(row.getAssetIds().size());			
	}


	/**
	 * @param jpegImageFile
	 * @return NULL if not an image or image is from an Apple or Google device--the
	 *         metadata otherwise
	 */
	public static JpegImageMetadata shouldAdjustExif(byte[] jpegImageFile) {
		ImageMetadata metadata;
		try {
			metadata = Imaging.getMetadata(jpegImageFile);
		} catch (ImageReadException | IOException e) {
			e.printStackTrace();
			return null;
		}

		if (!(metadata instanceof JpegImageMetadata))
			return null;

		JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

		// Don't adjust EXIF offset for mobile phones
		List<ImageMetadataItem> items = jpegMetadata.getItems();
		for (ImageMetadataItem item : items) {
			String itemStr = item.toString();
			if (StringUtils.contains(itemStr, "Make") && StringUtils.containsAny(itemStr, "Apple", "Google"))
				return null;
		}

		return jpegMetadata;
	}

	/**
	 * Changes the "DateTimeOriginal" Exif tag of a JPEG image file to a new date
	 * and time value.
	 * 
	 * @param jpegImageFile The JPEG image file to modify.
	 * @param dest          The destination file where the modified image will be
	 *                      saved.
	 * @param newDateTime   The new date and time value to set for the
	 *                      "DateTimeOriginal" tag.
	 * @return {@code true} if the modification was successful, {@code false}
	 *         otherwise.
	 * @throws FileNotFoundException If the JPEG image file or the destination file
	 *                               is not found.
	 * @throws IOException           If an I/O error occurs while reading or writing
	 *                               the files.
	 */
	private static boolean changeDateTimeOrig(byte[] jpegImageFile, Path dest, String newDateTime)
			throws FileNotFoundException, IOException {
		JpegImageMetadata jpegMetadata = shouldAdjustExif(jpegImageFile);
		if (jpegMetadata == null)
			return false;

		try (OutputStream fos = Files.newOutputStream(dest); OutputStream os = new BufferedOutputStream(fos)) {
			TiffImageMetadata exif = jpegMetadata.getExif();

			TiffOutputSet outputSet = null;
			if (null != exif) {
				outputSet = exif.getOutputSet();
			}

			if (null == outputSet) {
				outputSet = new TiffOutputSet();
			}

			TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
			exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
			exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, newDateTime);
			new ExifRewriter().updateExifMetadataLossless(jpegImageFile, os, outputSet);
		} catch (ImageWriteException | ImageReadException e) {
			logger.error("Error adjusting EXIF data", e);
			return false;
		}

		return true;
	}

	private static boolean isEligibleMediaFile(Path file) {
		if (Files.isDirectory(file) || Files.isSymbolicLink(file))
			return false;

		String fileExt = getFileExtension(file.getFileName().toString()).toLowerCase();

		boolean isImage = imageExtensions.contains(fileExt);
		boolean isAudio = audioExtensions.contains(fileExt);
		boolean isVideo = videoExtensions.contains(fileExt);

		return isImage || isAudio || isVideo;
	}

	private void moveFile(Path from, Path to) throws IOException {
		if (msc.isUseSymbolicLinks())
			Files.createSymbolicLink(to, from);
		else {
			if (Files.exists(to)) {
				logger.error(to + " already exists!! Source file left in original location.");
				return;
			}
			Files.move(from, to);
		}
	}

	private Path createDirIfNotExists(Path path) throws IOException {
		return Files.createDirectories(path);
	}

	public static String getFileExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
	}

	private void transcodeVideo(Path file, Path parentDir) throws IOException {
		long fileSizeInBytes = Files.size(file);
		long fileSizeInMB = fileSizeInBytes / (1024 * 1024);
		String fileName = file.getFileName().toString();
		if (fileSizeInMB > MAX_ML_UPLOAD_SIZE_VIDEO) {
			String outputFileName = fileName.replaceFirst("[.][^.]+$", "") + TRANSCODED_VIDEO_SUFFIX + ".mp4";
			Path outputFile = file.getParent().resolve(outputFileName);
			Path finalOutputFile = parentDir.resolve(outputFileName);
			if (Files.notExists(outputFile) && Files.notExists(finalOutputFile)) {
				logger.info(fileName + " too large for ML upload, transcoding with ffmpeg...");
				String convVideoPath = parentDir + File.separator + outputFileName;
				String[] command = { "ffmpeg", "-i", file.toString(), "-map_metadata", "0:s:0", "-c:v", "libx264",
						"-crf", "22", "-preset", "medium", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
						"-max_muxing_queue_size", "1024", convVideoPath };

				ProcessBuilder pb = new ProcessBuilder(command);
				pb.redirectError(ProcessBuilder.Redirect.DISCARD);
				pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

				try {
					process = pb.start();
					int res = process.waitFor();
					if (res == 0)
						logger.info("Saved converted video to: " + convVideoPath);
				} catch (InterruptedException | IOException e) {
					logger.error("Cannot transcode video to smaller size", e);
				} finally {
					if (process != null) {
						process.destroy();
					}
				}
			}
		}
	}

	private void checkMetadataAndMove(Path file, Path outputDir, Long hrsOffset, Set<String> subIds, boolean sepYearDir,
			FolderGroup folderGroup) throws IOException {
		LocalDateTime mediaTime = null;
		for (CreationDateProvider cdp : creationDateProviders) {
			mediaTime = cdp.findCreationDate(file, hrsOffset);
			if (mediaTime != null)
				break;
		}

		Path grandParentDir = outputDir;
		if (sepYearDir)
			grandParentDir = grandParentDir.resolve(Path.of(String.valueOf(mediaTime.getYear())));

		String subId = null;
		if (mediaTime != null)
			subId = rangeMap.get(mediaTime);

		String mediaDateStr = mediaTime.format(folderDtf);

		Path parentDir = grandParentDir;
		if (subId != null) {
			SubStats ss = checklistStatsMap.get(subId);

			String locNameAbbrev = StringUtils.abbreviate(ss.getLocName(), StringUtils.EMPTY, 40);
			locNameAbbrev = StringUtils.replaceEach(locNameAbbrev, invalidChars, validChars);

			String folderNameInfo = StringUtils.EMPTY;
			switch (folderGroup) {
			case location:
				parentDir = parentDir.resolve(ss.getSubnational1Code());
				if (ss.getCounty() != null)
					parentDir = parentDir.resolve(ss.getCounty());
				folderNameInfo = mediaDateStr;
				parentDir = parentDir.resolve(locNameAbbrev);
				break;

			case date:
				parentDir = parentDir.resolve(mediaDateStr);
				folderNameInfo = ss.getSubnational1Code() + "_" + ss.getCounty() + "_" + locNameAbbrev;
				break;

			default:
				break;
			}

			String folderName = folderNameInfo + "_" + subId;
			parentDir = parentDir.resolve(folderName);

			subIds.add(subId);
			ss.incNumAssetsLocal();
		} else
			parentDir = parentDir.resolve(mediaDateStr);

		createDirIfNotExists(parentDir);

		String fileName = file.getFileName().toString();
		Path outputFile = parentDir.resolve(fileName);

		String fileExt = getFileExtension(fileName).toLowerCase();
		boolean isImage = imageExtensions.contains(fileExt);
		logger.debug("Processing " + file);
		if (hrsOffset != 0l && isImage) {
			String newDateTime = mediaTime.format(imageDtf);
			if (changeDateTimeOrig(Files.readAllBytes(file), outputFile, newDateTime)) {
				logger.debug("Adjusted EXIF date of " + file + " to " + newDateTime);
				Files.delete(file);
				return;
			}
		}

		if (msc.isTranscodeVideos() && fileExt.equals("mp4") && !fileName.contains(TRANSCODED_VIDEO_SUFFIX))
			transcodeVideo(file, parentDir);

		moveFile(file, outputFile);
	}

	/**
	 * Executes the main logic of the media processing task. This method is called
	 * when the task is executed.
	 * 
	 * @return The path to the generated index file, or null if no eligible media
	 *         files are found.
	 * @throws Exception If an error occurs during the execution of the task.
	 */
	@Override
	protected Path call() throws Exception {
		if (msc.getCsvFile() != null && msc.isReParseCsv())
		{
			rangeMap.clear();
			checklistStatsMap.clear();
			
			EbirdCsvParser.parseCsv(msc.getCsvFile(),ParseMode.parallel,PreSort.none,this::parseCsvLine);
			
			msc.setReParseCsv(false);			
		}

		Path mediaPath = msc.getMediaPath();

		// make output directory inside the provided media folder
		String outputDirName = OUTPUT_FOLDER_NAME + "_" + new Date().getTime();
		Path outputDir = mediaPath.resolve(outputDirName);

		Set<String> subIds = new TreeSet<>();
		Long hrsOffset = msc.getHrsOffset();

		AtomicInteger i = new AtomicInteger(0);
		List<Path> eligibleFiles = new ArrayList<>();
		logger.info("Analyzing files...");

		try (Stream<Path> stream = Files.walk(mediaPath)) {
			stream.filter(MediaSortTask::isEligibleMediaFile).forEach(file -> {
				eligibleFiles.add(file);
				i.incrementAndGet();
				if (i.get() % 100 == 0)
					logger.info("Added 100 files to processing queue (" + i + " total)...");
			});
		}

		if (eligibleFiles.isEmpty()) {
			logger.info("No eligible media files found.");
			return null;
		}

		boolean sepYearDir = msc.isSepYear();
		int numFiles = eligibleFiles.size();
		int j = 1;
		logger.info("Processing " + numFiles + " files in " + mediaPath + " and subdirectories...");
		for (Path f : eligibleFiles) {
			checkMetadataAndMove(f, outputDir, hrsOffset, subIds, sepYearDir, msc.getFolderGroup());
			final double progPer = j++ / ((double) numFiles);
			updateProgress(progPer, 1.0);
		}

		// Move all files out of temp parent directory we created
		if (!msc.isCreateParentDir()) {
			List<Path> dirToMove = new ArrayList<>();
			try (Stream<Path> stream = Files.walk(outputDir, 1)) {
				stream.filter(path -> !path.equals(outputDir)) // Skip moving the source directory itself
						.forEach(path -> {
							dirToMove.add(path);
						});
			}

			for (Path directory : dirToMove) {
				if (!Files.exists(directory))
					continue;

				Path newPath = mediaPath.resolve(outputDir.relativize(directory));
				Files.move(directory, newPath, StandardCopyOption.ATOMIC_MOVE);
			}

			Files.delete(outputDir);
		} else {
			Path finalOutputDir = mediaPath.resolve(OUTPUT_FOLDER_NAME);
			if (Files.exists(finalOutputDir))
				logger.error("Directory " + finalOutputDir + " already exists! Check " + outputDir + " for results.");
			else
				Files.move(outputDir, finalOutputDir, StandardCopyOption.ATOMIC_MOVE);
		}
		
		Path resultsFile = null;
        if (!subIds.isEmpty()) {
        	resultsFile = mediaPath.resolve("checklistIndex_" + new Date().getTime() + ".csv");
            try (BufferedWriter bw = Files.newBufferedWriter(resultsFile, StandardCharsets.UTF_8,StandardOpenOption.CREATE);
                 CSVPrinter csvPrinter = new CSVPrinter(bw, CSVFormat.DEFAULT.builder().setHeader("Checklist Link", "Date", "State", "County", "Num Uploaded Assets", "Num Local Assets").build())) 
            {
                for (String subId : subIds) {
                    SubStats ss = checklistStatsMap.get(subId);
                    csvPrinter.printRecord("https://ebird.org/checklist/" + subId + "/media",ss.getDate(), ss.getSubnational1Code(), ss.getCounty(),ss.getNumAssetsUploaded(), ss.getNumAssetsLocal());
                }
                csvPrinter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
		updateProgress(1.0, 1.0);

		logger.info("ALL DONE! :-)");
		return resultsFile;

	}

	public Process getProcess() {
		return process;
	}

}