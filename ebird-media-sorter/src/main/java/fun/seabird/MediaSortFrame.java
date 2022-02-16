package fun.seabird;

import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

public class MediaSortFrame extends JFrame 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -960332919832074912L;	
	
	private static String titleText = "eBird Media Sorter";	
	private static String subDirText = "Create Subdirectory";
	private static String browseBtnText = "Choose Media Directory";
	private static String resBtnText = "See Results!";
	private static String exifAdjText = "EXIF Adjustment (in hours)";
	private static String csvBtnText = "Choose MyEBirdData CSV File";
	private static String sepYearText = "Group Date Folders by Year";
	private static String symbLinkText = "Generate Symbolic Links Instead of Moving Files"; 
	private static String runBtnText = "Run"; 
	
	private class CsvFileFilter extends FileFilter
	{
		@Override
		public boolean accept(File f) {
			return f.isDirectory() || f.getName().endsWith("csv");
		}

		@Override
		public String getDescription() {
			return "CSV Files";
		}
	}
		
	private class CustomOutputStream extends OutputStream {
	    private JTextArea textArea;
	     
	    public CustomOutputStream(JTextArea textArea) {
	        this.textArea = textArea;
	    }
	     
	    @Override
	    public void write(int b) throws IOException {
	        // redirects data to the text area
	        textArea.append(String.valueOf((char)b));
	        // scrolls the text area to the end of data
	        textArea.setCaretPosition(textArea.getDocument().getLength());
	    }
	}

	public MediaSortFrame() throws HeadlessException 
	{
		super(titleText);
		
		final MediaSortCmd msc = new MediaSortCmd();
		final MediaSortResult msr = new MediaSortResult();
		
		JButton browseBut = new JButton(browseBtnText);
		JLabel browseButLbl = new JLabel();
		
		JLabel offsetLbl = new JLabel(exifAdjText);		
		JSlider offsetSlider = new JSlider(-6,6,0);
		offsetSlider.setMajorTickSpacing(1);
		offsetSlider.setPaintTicks(true);
		offsetSlider.setPaintLabels(true);
		offsetSlider.setSnapToTicks(true);
		
		JButton csvBrowse = new JButton(csvBtnText);
		JLabel csvBrowseLbl = new JLabel();
		
		JCheckBox sepYearDirCb = new JCheckBox(sepYearText,false);
		JCheckBox parentDirCb = new JCheckBox(subDirText,true);
		JCheckBox symbLinkCb = new JCheckBox(symbLinkText,false);
		
		JButton runBut = new JButton(runBtnText);
		runBut.setEnabled(false);
		
		JProgressBar pb = new JProgressBar();
		pb.setVisible(false);
		pb.setValue(0);
		pb.setStringPainted(true);
		msc.setPb(pb);
		
		//Log output
		JTextArea outputLog = new JTextArea();
		outputLog.setLineWrap(true);
		outputLog.setEditable(false);		
		JScrollPane scroll = new JScrollPane (outputLog);
	    scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
	    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
	    PrintStream printStream = new PrintStream(new CustomOutputStream(outputLog));
		System.setOut(printStream);
		System.setErr(printStream);
		
		JButton resBtn = new JButton(resBtnText);
		resBtn.setEnabled(false);
		resBtn.setVisible(false);
		
		final JFrame mediaFrame = this;	
		
		browseBut.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser fc = new JFileChooser();	
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int returnVal = fc.showOpenDialog(mediaFrame);

		        if (returnVal == JFileChooser.APPROVE_OPTION)
		        {
		        	String path = fc.getSelectedFile().getPath();
		        	msc.setMediaPath(path);
		        	browseButLbl.setText(path);
		        	runBut.setEnabled(true);			        	
		        	parentDirCb.setText(subDirText + " " + msc.getMediaPath() + File.separator + MediaSorterRunner.OUTPUT_FOLDER_NAME);
		        }
			}
		});
		
		offsetSlider.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e) {
				int offset = offsetSlider.getValue();				
				msc.setHrsOffset(Long.valueOf(offset));
				
				if (offset != 0)
				{
					msc.setUseSymbolicLinks(false);
					symbLinkCb.setSelected(false);
					symbLinkCb.setEnabled(false);
				}
				else
				{
					symbLinkCb.setEnabled(true);
				}
			}			
		});		
		
		csvBrowse.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser fc = new JFileChooser();	
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				fc.setFileFilter(new CsvFileFilter());
				int returnVal = fc.showOpenDialog(mediaFrame);

		        if (returnVal == JFileChooser.APPROVE_OPTION)
		        {
		        	File f = fc.getSelectedFile();
		        	msc.setCsvFile(f); 
		        	csvBrowseLbl.setText(f.getPath());
		        }
			}
		});
		
		sepYearDirCb.addItemListener(new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e) 
			{
				msc.setSepYear(e.getStateChange() == ItemEvent.SELECTED);				
			}
		});
		
		parentDirCb.addItemListener(new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e) 
			{
				msc.setCreateParentDir(e.getStateChange() == ItemEvent.SELECTED);				
			}
		});
		
		symbLinkCb.addItemListener(new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e) 
			{
				msc.setUseSymbolicLinks(e.getStateChange() == ItemEvent.SELECTED);				
			}
		});
		
		runBut.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				new Thread(() -> {
					runBut.setEnabled(false);
					resBtn.setEnabled(false);
					resBtn.setVisible(false);
					
					pb.setValue(0);
					pb.setVisible(true);
					
					Document log = outputLog.getDocument();
					try {
						log.remove(0,log.getLength());
					} catch (BadLocationException e2) {
						e2.printStackTrace();
					}
					try {
						msr.setIndexPath(new MediaSorterRunner(msc).run());
						if (msr.getIndexPath() != null)
						{
							resBtn.setEnabled(true);
							resBtn.setVisible(true);
						}
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					finally 
					{
						runBut.setEnabled(true);
					}
				}).start();
			}
		});
		
		resBtn.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				Desktop desktop = Desktop.getDesktop();
				File resFile = new File(msr.getIndexPath().toUri());
				if(resFile.exists())
					try {
						desktop.open(resFile);
					} catch (IOException e1) {						
						e1.printStackTrace();
					} 
			}
		});	
		
		JPanel jpMain = new JPanel();
		JPanel jp1 = new JPanel();
		JPanel jp2 = new JPanel();
		JPanel jp3 = new JPanel();
		JPanel jp4 = new JPanel();
		JPanel jp5 = new JPanel();
		JPanel jp6 = new JPanel();
		
		jp1.setLayout(new GridLayout(1,2,10,10));
		jp1.add(browseBut);
		jp1.add(browseButLbl);
		
		jp2.setLayout(new GridLayout(1,2,10,10));
		jp2.add(offsetLbl);
		jp2.add(offsetSlider);
		
		jp3.setLayout(new GridLayout(1,2,10,10));
		jp3.add(csvBrowse);
		jp3.add(csvBrowseLbl);
		
		jp4.setLayout(new GridLayout(5,1,10,10));
		jp4.add(sepYearDirCb);
		jp4.add(parentDirCb);
		jp4.add(symbLinkCb);
		jp4.add(runBut);		
		
		jp5.add(pb);
		
		jp6.setLayout(new GridLayout(2,1));		
		jp6.add(scroll);
		jp6.add(resBtn);
		
		jpMain.setLayout(new BoxLayout (jpMain, BoxLayout.Y_AXIS));
		jpMain.add(jp1);
		jpMain.add(jp2);
		jpMain.add(jp3);
		jpMain.add(jp4);
		jpMain.add(jp5);
		jpMain.add(jp6);

		add(jpMain);
		
		setSize(600,800);
		
		//exit on window close
		setDefaultCloseOperation(EXIT_ON_CLOSE);	
		          
		setVisible(true);//making the frame visible 
	}
	

}
