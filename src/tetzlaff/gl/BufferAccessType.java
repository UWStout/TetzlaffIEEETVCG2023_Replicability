package tetzlaff.gl;

public enum BufferAccessType 
{
    /**
     * The buffer will be primarily written to by the application and read by shaders.
     */
    DRAW,

    /**
     * The buffer will be primarily written to by shaders and read by the application.
     */
    READ,

    /**
     * The buffer will be primarily both written to and read from shaders.
     */
    COPY
}
