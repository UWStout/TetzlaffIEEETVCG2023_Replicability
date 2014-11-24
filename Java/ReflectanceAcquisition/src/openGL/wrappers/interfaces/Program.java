package openGL.wrappers.interfaces;
public interface Program 
{

	void attachShader(Shader shader, boolean owned);

	void detachShader(Shader shader);

	boolean isLinked();

	void link();

	void use();

	void delete();

	void setUniform(String name, double value1, double value2,
			double value3, double value4);

	void setUniform(String name, double value1, double value2,
			double value3);

	void setUniform(String name, double value1, double value2);

	void setUniform(String name, double value);

	void setUniform(String name, float value1, float value2,
			float value3, float value4);

	void setUniform(String name, float value1, float value2,
			float value3);

	void setUniform(String name, float value1, float value2);

	void setUniform(String name, float value);

	void setUniform(String name, int value1, int value2,
			int value3, int value4);

	void setUniform(String name, int value1, int value2,
			int value3);

	void setUniform(String name, int value1, int value2);

	void setUniform(String name, int value);

	void setUniform(int location, double value1, double value2,
			double value3, double value4);

	void setUniform(int location, double value1, double value2,
			double value3);

	void setUniform(int location, double value1, double value2);

	void setUniform(int location, double value);

	void setUniform(int location, float value1, float value2,
			float value3, float value4);

	void setUniform(int location, float value1, float value2,
			float value3);

	void setUniform(int location, float value1, float value2);

	void setUniform(int location, float value);

	void setUniform(int location, int value1, int value2,
			int value3, int value4);

	void setUniform(int location, int value1, int value2,
			int value3);

	void setUniform(int location, int value1, int value2);

	void setUniform(int location, int value);

	int getUniformLocation(String name);

	int getId();

}