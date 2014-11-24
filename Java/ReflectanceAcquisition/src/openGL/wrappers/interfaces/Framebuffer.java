package openGL.wrappers.interfaces;

import java.io.IOException;

public interface Framebuffer 
{
	int getViewportX();

	int getViewportY();

	int getViewportWidth();

	int getViewportHeight();

	void setViewport(int x, int y, int width, int height);

	void bindForDraw();

	void bindForRead(int attachmentIndex);

	int[] readPixelsRGBA(int mode);

	void saveToFile(int readMode, String fileFormat, String filename) throws IOException;
}