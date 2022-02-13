package fun.seabird;

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
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

public class MediaSortFrame extends JFrame 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -960332919832074912L;	
	
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
		super("eBird Media Sorter");
		
		final MediaSortCmd msc = new MediaSortCmd();
		
		JButton browseBut = new JButton("Choose Media Directory");
		JLabel browseButLbl = new JLabel();
		JLabel offsetLbl = new JLabel("Adjust EXIF (in hours)");

		JButton runBut = new JButton("Run");
		runBut.setEnabled(false);
		JButton csvBrowse = new JButton("Choose CSV File");
		JLabel csvBrowseLbl = new JLabel();
		
		JTextArea outputLog = new JTextArea();
		outputLog.setEditable(false);

		JProgressBar pb = new JProgressBar();
		
		final JFrame mediaFrame = this;
		
		JSlider t1 = new JSlider(-6,6,0);
		t1.setMajorTickSpacing(1);
		t1.setPaintTicks(true);
		t1.setPaintLabels(true);
		t1.setSnapToTicks(true);
		t1.addChangeListener(new ChangeListener()
		{
			@Override
			public void stateChanged(ChangeEvent e) {
				msc.setHrsOffset(Long.valueOf(t1.getValue()));
			}			
		});

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
			        }
				}
			}
		);
		
		csvBrowse.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				JFileChooser fc = new JFileChooser();	
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				int returnVal = fc.showOpenDialog(mediaFrame);

		        if (returnVal == JFileChooser.APPROVE_OPTION)
		        {
		        	File f = fc.getSelectedFile();
		        	msc.setCsvFile(f); 
		        	csvBrowseLbl.setText(f.getPath());
		        }
			}
		});
		
		JCheckBox cb = new JCheckBox("Folder for Year");
		cb.addItemListener(new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e) 
			{
				msc.setSepYear(e.getStateChange() == 1);				
			}
		});		
		
		runBut.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				new Thread(() -> {
					runBut.setEnabled(false);
					
					Document log = outputLog.getDocument();
					try {
						log.remove(0,log.getLength());
					} catch (BadLocationException e2) {
						e2.printStackTrace();
					}
					try {
						new MediaSorterRunner(msc).run();
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
		
		//exit on window close
		setDefaultCloseOperation(EXIT_ON_CLOSE);		
		
		JPanel jp = new JPanel();
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
		jp2.add(t1);
		
		jp3.setLayout(new GridLayout(1,2,10,10));
		jp3.add(csvBrowse);
		jp3.add(csvBrowseLbl);
		
		jp4.setLayout(new GridLayout(2,1,10,10));
		jp4.add(cb);
		jp4.add(runBut);			
		
		pb.setValue(0);
		pb.setBounds(0,0,420,50);
		pb.setStringPainted(true);
		msc.setPb(pb);
		
		jp5.add(pb);
		
		jp6.setLayout(new GridLayout(1,1));
		PrintStream printStream = new PrintStream(new CustomOutputStream(outputLog));
		System.setOut(printStream);
		System.setErr(printStream);
		jp6.add(outputLog);	
		
		jp.setLayout(new BoxLayout (jp, BoxLayout.Y_AXIS));
		jp.add(jp1);
		jp.add(jp2);
		jp.add(jp3);
		jp.add(jp4);
		jp.add(jp5);
		jp.add(jp6);

		add(jp);
		
		setSize(500,300);
		          
		setVisible(true);//making the frame visible 
	}
	

}
