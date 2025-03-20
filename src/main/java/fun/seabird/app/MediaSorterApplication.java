package fun.seabird.app;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ResourceBundle;

import fun.seabird.sorter.FolderGroup;
import fun.seabird.sorter.MediaSortCmd;
import fun.seabird.sorter.MediaSortResult;
import fun.seabird.sorter.MediaSortTask;
import fun.seabird.util.MediaSortConstants;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class MediaSorterApplication extends Application 
{
	private static final int FRAME_WIDTH = 650;
	private static final int FRAME_HEIGHT = 800;
	
	private static final String UI_PROPERTIES_FILE_BASE = "ui";
		
	private static final ExtensionFilter csvFilter = new ExtensionFilter("CSV Files","*.csv");
	
	public static final TextArea OUTPUT_LOG = new TextArea();	
	
	/**
	Executes a media sort task when the "Run" button is clicked.
	Disables the "Run" and "Reset" buttons, and shows a progress bar and scroll bar.
	Clears the output log and creates a new media sort task.
	Binds the progress property of the progress bar to the progress property of the media sort task.
	Starts the media sort task on a separate thread.
	When the media sort task is successful, enables the "Reset" button and sets its visibility to true.
	Sets the index path of the media sorter to the value returned by the media sort task.
	If the index path is not null and the desktop is supported, enables the "Reset" button and sets its visibility to true.
	Adds a shutdown hook to wait for the media sort task process to finish and destroy it if necessary.
	* @param runBut the button that triggers the media sorting task
    * @param resBtn the button that displays the results of the media sorting task
    * @param pb the progress bar that displays the progress of the media sorting task
    * @param scroll  the scroll pane that contains the media sorting task output log
    * @param msc the media sorting configuration to be used for the task
    * @param msr the media sorting results to be updated after the task completes
	*/
	private static void runMediaSortTask(Button runBut, Button resBtn, ProgressBar pb, ScrollPane scroll, MediaSortCmd msc, MediaSortResult msr) 
	{
	    runBut.setDisable(true);
	    resBtn.setDisable(true);
	    resBtn.setVisible(false);
	    pb.setVisible(true);
	    scroll.setVisible(true);

	    OUTPUT_LOG.clear();

	    MediaSortTask task = new MediaSortTask(msc);

	    pb.progressProperty().bind(task.progressProperty());

	    Thread taskThr = new Thread(task);
	    taskThr.setDaemon(true);
	    taskThr.start();

	    task.setOnSucceeded(_ -> {
	        Path path = task.getValue();
	        runBut.setDisable(false);

	        msr.setIndexPath(path);
	        if (msr.getIndexPath() != null && Desktop.isDesktopSupported()) {
	            resBtn.setDisable(false);
	            resBtn.setVisible(true);
	        }
	    });

	    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	        if (task.getProcess() != null) {
	            try {
	                task.getProcess().waitFor();
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	            task.getProcess().destroy();
	        }
	    }));
	}
	
	@Override
	public void start(Stage s) throws Exception 
	{	
		ResourceBundle msgs = ResourceBundle.getBundle(UI_PROPERTIES_FILE_BASE);
		
		s.setTitle(msgs.getString("titleText"));
		
		final MediaSortCmd msc = new MediaSortCmd();
		final MediaSortResult msr = new MediaSortResult();
		
		Label introLbl = new Label(msgs.getString("introText"));
		
		Button browseBut = new Button(msgs.getString("browseBtnText"));
		Label browseButLbl = new Label();
		
		Label offsetLbl = new Label(msgs.getString("exifAdjText"));		
		Slider offsetSlider = new Slider(-6,6,0);		
		offsetSlider.setSnapToTicks(true);
		offsetSlider.setMajorTickUnit(1);
		
		Button csvBrowse = new Button(msgs.getString("csvBtnText"));
		Label csvBrowseLbl = new Label();
		
		CheckBox locSortCb = new CheckBox(msgs.getString("locSortText"));
		CheckBox sepYearDirCb = new CheckBox(msgs.getString("sepYearText"));
		CheckBox parentDirCb = new CheckBox(msgs.getString("subDirText"));
		parentDirCb.setSelected(true);
		CheckBox symbLinkCb = new CheckBox(msgs.getString("symbLinkText"));		
		CheckBox transcodeVidCb = new CheckBox(msgs.getString("transcodeVidText"));
		
		Button runBut = new Button(msgs.getString("runBtnText"));
		runBut.setDisable(true);
		
		ProgressBar pb = new ProgressBar(0.0);
		pb.setVisible(false);		
		
		//Log output
		OUTPUT_LOG.setWrapText(true);
		OUTPUT_LOG.setEditable(false);
		ScrollPane scroll = new ScrollPane (OUTPUT_LOG);
		scroll.setVisible(false);
		
		Button resBtn = new Button(msgs.getString("resBtnText"));
		resBtn.setDisable(true);
		resBtn.setVisible(false);		
		
		browseBut.setOnAction(_ -> 
			{
				DirectoryChooser dc = new DirectoryChooser();
				
				File selectedFile = dc.showDialog(browseBut.getScene().getWindow());
				
				if (selectedFile != null)
				{
					String path = selectedFile.getPath();
		        	msc.setMediaPath(selectedFile.toPath());
		        	browseButLbl.setText(path);
		        	runBut.setDisable(false);			        	
		        	parentDirCb.setText(msgs.getString("subDirText") + " " + msc.getMediaPath().resolve(MediaSortConstants.OUTPUT_FOLDER_NAME));
				}
		   }
		);
			
		offsetSlider.valueProperty().addListener((_, _, newValue) ->
		{
			int offset = newValue.intValue();
			msc.setHrsOffset(Long.valueOf(offset));
			
			if (offset != 0)
				offsetLbl.setText("EXIF Adjustment (" + offset + " hours)");
			else
				offsetLbl.setText(msgs.getString("exifAdjText"));
			
			offsetSlider.setValue(newValue.doubleValue());
		});	
		
		csvBrowse.setOnAction(_ ->
		{
			FileChooser fc = new FileChooser();	
			fc.getExtensionFilters().add(csvFilter);
			
			File f = fc.showOpenDialog(csvBrowse.getScene().getWindow());
			
			if (f != null)
			{
				msc.setReParseCsv(true);
				msc.setCsvFile(f.toPath()); 
				csvBrowseLbl.setText(f.getPath());
			}
		});
		
		sepYearDirCb.setOnAction(_ ->
			{
					msc.setSepYear(sepYearDirCb.isSelected());				
			}
		);
		
		locSortCb.setOnAction(_ ->
			{
				if (locSortCb.isSelected())
					msc.setFolderGroup(FolderGroup.location);
				else
					msc.setFolderGroup(FolderGroup.date);
			}
		);
		
		parentDirCb.setOnAction(_ ->
			{
				msc.setCreateParentDir(parentDirCb.isSelected());				
			}
		);
		
		symbLinkCb.setOnAction(_ ->
		{
				msc.setUseSymbolicLinks(symbLinkCb.isSelected());				
		});
		
		transcodeVidCb.setOnAction(_ ->
		{
				msc.setTranscodeVideos(transcodeVidCb.isSelected());				
		});		
		
		runBut.setOnAction(_ -> runMediaSortTask(runBut, resBtn, pb, scroll, msc, msr));
		
		resBtn.setOnAction(_ ->
		{
			Desktop desktop = Desktop.getDesktop();
			File resFile = new File(msr.getIndexPath().toUri());
			if (resFile.exists())				
				new Thread(() -> {
					try {
						desktop.open(resFile);
					} catch (IOException e) {
						System.err.println(e);
					}
				}).start();
			}
		);			
		
		GridPane gp = new GridPane();	
		
		gp.setVgap(20.0);
				
		gp.add(introLbl,0,0,3,1);
		gp.add(new HBox(10,browseBut,browseButLbl),0,1,3,1);		
		
		gp.add(new HBox(10,csvBrowse,csvBrowseLbl),0,2,3,1);
		
		gp.add(parentDirCb,0,3,3,1);
		gp.add(sepYearDirCb,0,4,3,1);
		gp.add(locSortCb,0,5,3,1);
		gp.add(symbLinkCb,0,6,3,1);
		gp.add(transcodeVidCb,0,7,3,1);
		
		gp.add(new HBox(10,offsetSlider,offsetLbl),0,8,3,1);
		
		gp.add(new Separator(),0,9,3,1);
		
		gp.add(runBut,0,10,3,1);
		
		gp.add(pb,0,11,3,2);
		
		gp.add(scroll,0,13,3,1);
		gp.add(resBtn,0,17,3,1);	
					
		Scene scene = new Scene(gp,FRAME_WIDTH,FRAME_HEIGHT);
		Insets padding = new Insets(10,0,10,30); //padding on the left side
		((Region) scene.getRoot()).setPadding(padding);
		s.setScene(scene);
		s.setResizable(true);
		s.show();	
	}
}
