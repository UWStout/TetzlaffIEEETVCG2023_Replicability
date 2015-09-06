package tetzlaff.halfwayfield;

import java.io.File;
import java.io.IOException;

import tetzlaff.gl.Context;
import tetzlaff.gl.Program;
import tetzlaff.gl.helpers.Trackball;
import tetzlaff.ulf.ULFDrawableListModel;
import tetzlaff.ulf.ULFLoadOptions;

public class HalfwayFieldRendererList<ContextType extends Context<ContextType>> extends ULFDrawableListModel<ContextType>
{
	private static final long serialVersionUID = -8199166231586786343L;
	private Trackball lightTrackball;

	public HalfwayFieldRendererList(ContextType context, Program<ContextType> program, Trackball viewTrackball, Trackball lightTrackball) 
	{
		super(context, program, viewTrackball);
		this.lightTrackball = lightTrackball;
	}
	
	@Override
	protected HalfwayFieldRenderer<ContextType> createFromVSETFile(File vsetFile, ULFLoadOptions loadOptions) throws IOException
	{
		return new HalfwayFieldRenderer<ContextType>(context, this.getProgram(), vsetFile, null, loadOptions, trackball, lightTrackball);
	}
	
	@Override
	protected HalfwayFieldRenderer<ContextType> createFromAgisoftXMLFile(File xmlFile, File meshFile, ULFLoadOptions loadOptions) throws IOException
	{
		return new HalfwayFieldRenderer<ContextType>(context, this.getProgram(), xmlFile, meshFile, loadOptions, trackball, lightTrackball);
	}
	
	@Override
	protected HalfwayFieldRenderer<ContextType> createMorphFromLFMFile(File lfmFile, ULFLoadOptions loadOptions) throws IOException
	{
		throw new IllegalStateException("Morphs not supported for halfway field rendering.");
		//return new ULFMorphRenderer<ContextType>(context, this.getProgram(), lfmFile, loadOptions, trackball, lightTrackball);
	}
}
