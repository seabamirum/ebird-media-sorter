package fun.seabird;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import fun.seabird.MediaSortCmd.FolderGroup;
import javafx.application.Application;
import javafx.concurrent.Task;
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
	private static String titleText = "eBird Media Sorter";	
	
	private static int FRAME_WIDTH = 650;
	private static int FRAME_HEIGHT = 800;
	
	private static String introText = "Welcome! Choose your media directory, eBird CSV file, options, and press run.";	
	private static String subDirText = "Create Subdirectory";
	private static String browseBtnText = "Choose Media Directory";
	private static String resBtnText = "See Results!";
	private static String exifAdjText = "EXIF Adjustment (0 hours)";
	private static String csvBtnText = "Choose MyEBirdData CSV File";
	private static String sepYearText = "Create Parent Folders by Year";
	private static String locSortText = "Create Subfolders by Location";
	private static String symbLinkText = "Generate Symbolic Links Instead of Moving Files"; 
	private static String runBtnText = "Run"; 
	
	private ExtensionFilter csvFilter = new ExtensionFilter("CSV Files","*.csv");
	
	public static final TextArea OUTPUT_LOG = new TextArea();	
	
	@Override
	public void start(Stage s) throws Exception 
	{	
		s.setTitle(titleText);
		
		final MediaSortCmd msc = new MediaSortCmd();
		final MediaSortResult msr = new MediaSortResult();
		
		Label introLbl = new Label(introText);
		
		Button browseBut = new Button(browseBtnText);
		Label browseButLbl = new Label();
		
		Label offsetLbl = new Label(exifAdjText);		
		Slider offsetSlider = new Slider(-6,6,0);		
		offsetSlider.setSnapToTicks(true);
		offsetSlider.setMajorTickUnit(1);
		
		Button csvBrowse = new Button(csvBtnText);
		Label csvBrowseLbl = new Label();
		
		CheckBox locSortCb = new CheckBox(locSortText);
		CheckBox sepYearDirCb = new CheckBox(sepYearText);
		CheckBox parentDirCb = new CheckBox(subDirText);
		parentDirCb.setSelected(true);
		CheckBox symbLinkCb = new CheckBox(symbLinkText);
		
		Button runBut = new Button(runBtnText);
		runBut.setDisable(true);
		
		ProgressBar pb = new ProgressBar(0.0);
		pb.setVisible(false);		
		
		//Log output
		OUTPUT_LOG.setWrapText(true);
		OUTPUT_LOG.setEditable(false);
		ScrollPane scroll = new ScrollPane (OUTPUT_LOG);
		scroll.setVisible(false);
		
		Button resBtn = new Button(resBtnText);
		resBtn.setDisable(true);
		resBtn.setVisible(false);		
		
		browseBut.setOnAction(event -> 
			{
				DirectoryChooser dc = new DirectoryChooser();
				
				File selectedFile = dc.showDialog(browseBut.getScene().getWindow());
				
				if (selectedFile != null)
				{
					String path = selectedFile.getPath();
		        	msc.setMediaPath(path);
		        	browseButLbl.setText(path);
		        	runBut.setDisable(false);			        	
		        	parentDirCb.setText(subDirText + " " + msc.getMediaPath() + File.separator + MediaSortTask.OUTPUT_FOLDER_NAME);
				}
		   }
		);
			
		offsetSlider.valueProperty().addListener((observable, oldValue, newValue) ->
		{
				int offset = newValue.intValue();
				msc.setHrsOffset(Long.valueOf(offset));
				
				if (offset != 0)
				{
					msc.setUseSymbolicLinks(false);
					symbLinkCb.setSelected(false);
					symbLinkCb.setDisable(true);
					offsetLbl.setText("EXIF Adjustment (" + offset + " hours)");
				}
				else
				{
					offsetLbl.setText(exifAdjText);
					symbLinkCb.setDisable(false);
				}
				
				offsetSlider.setValue(newValue.doubleValue());
		}
		);	
		
		csvBrowse.setOnAction(event ->
		{
			FileChooser fc = new FileChooser();	
			fc.getExtensionFilters().add(csvFilter);
			
			File f = fc.showOpenDialog(csvBrowse.getScene().getWindow());
			
			if (f != null)
			{
				msc.setCsvFile(f); 
				csvBrowseLbl.setText(f.getPath());
			}
		});
		
		sepYearDirCb.setOnAction(event ->
			{
					msc.setSepYear(sepYearDirCb.isSelected());				
			}
		);
		
		locSortCb.setOnAction(event ->
			{
				if (locSortCb.isSelected())
					msc.setFolderGroup(FolderGroup.location);
				else
					msc.setFolderGroup(FolderGroup.date);
			}
		);
		
		parentDirCb.setOnAction(event ->
			{
				msc.setCreateParentDir(parentDirCb.isSelected());				
			}
		);
		
		symbLinkCb.setOnAction(event ->
		{
				msc.setUseSymbolicLinks(symbLinkCb.isSelected());				
		});
		
		runBut.setOnAction(event ->
		{
			runBut.setDisable(true);
			resBtn.setDisable(true);
			resBtn.setVisible(false);			
			pb.setVisible(true);
			scroll.setVisible(true);
			
			OUTPUT_LOG.clear();	
			
			Task<Path> task = new MediaSortTask(msc);
			
			pb.progressProperty().bind(task.progressProperty());
			
		    new Thread(task).start();
		    
		    task.setOnSucceeded(success ->
		    {
			    Path path = task.getValue();			
				runBut.setDisable(false);
				
			    msr.setIndexPath(path);
				if (msr.getIndexPath() != null && Desktop.isDesktopSupported())
				{
					resBtn.setDisable(false);
					resBtn.setVisible(true);
				}
		    });	
		}
		);
		
		resBtn.setOnAction(event ->
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
		
		gp.add(new HBox(10,offsetSlider,offsetLbl),0,7,3,1);
		
		gp.add(new Separator(),0,8,3,1);
		
		gp.add(runBut,0,9,3,1);
		
		gp.add(pb,0,10,3,2);
		
		gp.add(scroll,0,12,3,1);
		gp.add(resBtn,0,16,3,1);	
					
		Scene scene = new Scene(gp,FRAME_WIDTH,FRAME_HEIGHT);
		Insets padding = new Insets(10,0,10,30); //padding on the left side
		((Region) scene.getRoot()).setPadding(padding);
		s.setScene(scene);
		s.setResizable(true);
		s.show();
	}
}