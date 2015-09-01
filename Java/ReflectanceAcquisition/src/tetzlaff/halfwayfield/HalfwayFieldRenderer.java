package tetzlaff.halfwayfield;

import java.io.File;
import java.io.IOException;

import tetzlaff.gl.Context;
import tetzlaff.gl.Program;
import tetzlaff.gl.helpers.Trackball;
import tetzlaff.gl.helpers.Vector3;
import tetzlaff.ulf.ULFDrawable;
import tetzlaff.ulf.ULFLoadOptions;
import tetzlaff.ulf.ULFLoadingMonitor;
import tetzlaff.ulf.ULFRenderer;

public class HalfwayFieldRenderer<ContextType extends Context<ContextType>> implements ULFDrawable 
{
	private ContextType context;
	private Program<ContextType> program;
	private ULFRenderer<ContextType> ulfRenderer;
	private HalfwayField<ContextType> halfwayField;
	private Trackball lightTrackball;
	private ULFLoadingMonitor callback;
	
	public HalfwayFieldRenderer(ContextType context, Program<ContextType> program, File vsetFile, ULFLoadOptions loadOptions, Trackball viewTrackball, Trackball lightTrackball)
    {
		this.context = context;
		this.program = program;
		this.lightTrackball = lightTrackball;
    	this.ulfRenderer = new ULFRenderer<ContextType>(context, program, vsetFile, loadOptions, viewTrackball);
    }

    public HalfwayFieldRenderer(ContextType context, Program<ContextType> program, File xmlFile, File meshFile, ULFLoadOptions loadOptions, Trackball viewTrackball, Trackball lightTrackball)
    {
		this.context = context;
		this.program = program;
		this.lightTrackball = lightTrackball;
    	this.ulfRenderer = new ULFRenderer<ContextType>(context, program, xmlFile, meshFile, loadOptions, viewTrackball);
    }

	@Override
	public void initialize() 
	{
		ulfRenderer.initialize();
		try
		{
			halfwayField = new HalfwayField<ContextType>(ulfRenderer.getLightField(), context);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		
		if (callback != null)
		{
			callback.loadingComplete();
		}
	}

	@Override
	public void update() 
	{
	}

	@Override
	public void draw() 
	{
		if (halfwayField.diffuseTexture == null)
		{
			program.setUniform("useDiffuseTexture", false);
		}
		else
		{
			program.setUniform("useDiffuseTexture", true);
			program.setTexture("diffuseMap", halfwayField.diffuseTexture);
		}
		if (halfwayField.normalTexture == null)
		{
			program.setUniform("useNormalTexture", false);
		}
		else
		{
			program.setUniform("useNormalTexture", true);
			program.setTexture("normalMap", halfwayField.normalTexture);
		}
		program.setUniform("diffuseRemovalAmount", 1.0f);
		program.setUniform("lightPos", new Vector3(lightTrackball.getRotationMatrix().getColumn(2)).times(lightTrackball.getScale()));
		ulfRenderer.draw();
	}

	@Override
	public void cleanup() 
	{
		ulfRenderer.cleanup();
		halfwayField.diffuseTexture.delete();
		halfwayField.normalTexture.delete();
	}

	@Override
	public void setOnLoadCallback(ULFLoadingMonitor callback) 
	{
		this.callback = callback;
	}

	@Override
	public float getGamma() 
	{
		return ulfRenderer.getGamma();
	}

	@Override
	public float getWeightExponent() 
	{
		return ulfRenderer.getWeightExponent();
	}

	@Override
	public boolean isOcclusionEnabled() 
	{
		return ulfRenderer.isOcclusionEnabled();
	}

	@Override
	public float getOcclusionBias() 
	{
		return ulfRenderer.getOcclusionBias();
	}

	@Override
	public boolean getHalfResolution() 
	{
		return ulfRenderer.getHalfResolution();
	}

	@Override
	public void setGamma(float gamma) 
	{
		ulfRenderer.setGamma(gamma);
	}

	@Override
	public void setWeightExponent(float weightExponent)
	{
		ulfRenderer.setWeightExponent(weightExponent);
	}

	@Override
	public void setOcclusionEnabled(boolean occlusionEnabled) 
	{
		ulfRenderer.setOcclusionEnabled(occlusionEnabled);
	}

	@Override
	public void setOcclusionBias(float occlusionBias) 
	{
		ulfRenderer.setOcclusionBias(occlusionBias);
	}

	@Override
	public void setHalfResolution(boolean halfResEnabled) 
	{
		ulfRenderer.setHalfResolution(halfResEnabled);
	}

	@Override
	public void setMultisampling(boolean multisamplingEnabled) 
	{
		ulfRenderer.setMultisampling(multisamplingEnabled);
	}

	@Override
	public void requestResample(int width, int height, File targetVSETFile, File exportPath) throws IOException 
	{
		ulfRenderer.requestResample(width, height, targetVSETFile, exportPath);
	}
}
