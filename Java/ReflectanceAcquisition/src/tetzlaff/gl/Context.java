package tetzlaff.gl;

import tetzlaff.gl.builders.FramebufferObjectBuilder;

public interface Context 
{
	boolean isDestroyed();
	
	void makeContextCurrent();

	void flush();
	void finish();
	void swapBuffers();
	
	void destroy();

	FramebufferSize getFramebufferSize();
	
	void enableDepthTest();
	void disableDepthTest();
	
	void enableMultisampling();
	void disableMultisampling();
	
	void enableBackFaceCulling();
	void disableBackFaceCulling();

	void setAlphaBlendingFunction(AlphaBlendingFunction func);
	void disableAlphaBlending();
	
	FramebufferObjectBuilder<? extends Context> getFramebufferObjectBuilder(int width, int height);
}
