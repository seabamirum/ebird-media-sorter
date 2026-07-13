package dev.seabird.app;

import javafx.application.Application;

/**
 * Main entry point for the Media Sorter application.
 * 
 * <p>This class contains only the {@code main} method that launches
 * the JavaFX application.
 */
public abstract class MediaSorter 
{
	/**
     * Launches the JavaFX MediaSorterApplication.
     * 
     * @param args command line arguments passed to the application
     */
	public static void main(String[] args)
	{
		Application.launch(MediaSorterApplication.class, args);			
	} 
}