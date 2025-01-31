/*
 *  Copyright (c) Michael Tetzlaff 2022
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.ibrelight.app;//Created by alexk on 7/19/2017.

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.xml.sax.SAXException;
import tetzlaff.gl.glfw.WindowFactory;
import tetzlaff.gl.glfw.WindowImpl;
import tetzlaff.gl.interactive.InteractiveGraphics;
import tetzlaff.gl.opengl.OpenGLContext;
import tetzlaff.gl.vecmath.Matrix4;
import tetzlaff.gl.vecmath.Vector2;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.gl.window.Key;
import tetzlaff.gl.window.ModifierKeys;
import tetzlaff.gl.window.ModifierKeysBuilder;
import tetzlaff.gl.window.PollableWindow;
import tetzlaff.ibrelight.core.*;
import tetzlaff.ibrelight.javafx.MainApplication;
import tetzlaff.ibrelight.javafx.MultithreadModels;
import tetzlaff.ibrelight.rendering.IBRInstanceManager;
import tetzlaff.ibrelight.tools.DragToolType;
import tetzlaff.ibrelight.tools.KeyPressToolType;
import tetzlaff.ibrelight.tools.ToolBindingModel;
import tetzlaff.ibrelight.tools.ToolBindingModelImpl;
import tetzlaff.ibrelight.tools.ToolBox.Builder;
import tetzlaff.interactive.EventPollable;
import tetzlaff.interactive.InitializationException;
import tetzlaff.interactive.InteractiveApplication;
import tetzlaff.interactive.Refreshable;
import tetzlaff.models.*;
import tetzlaff.util.KeyPress;
import tetzlaff.util.MouseMode;
import tetzlaff.util.WindowBasedController;

public final class Rendering
{
    private Rendering()
    {
    }

    private static IBRRequestManager<OpenGLContext> requestQueue = null;

    public static IBRRequestManager<OpenGLContext> getRequestQueue()
    {
        return requestQueue;
    }

    public static void runProgram(String... args) throws InitializationException
    {
        runProgram(null, args);
    }

    public static void runProgram(Stage stage, String... args) throws InitializationException
    {
        System.getenv();
        System.setProperty("org.lwjgl.util.DEBUG", "true");

        // Check for and print supported image formats (some are not as easy as you would think)
        printSupportedImageFormats();

        // Create a GLFW window for integration with LWJGL (part of the 'view' in this MVC arrangement)
        try(PollableWindow<OpenGLContext> window =
            (stage == null ? WindowFactory.buildOpenGLWindow("IBRelight", 800, 800)
                : WindowFactory.buildJavaFXWindow(stage, "IBRelight", 800, 800))
                    .setResizable(true)
                    .setMultisamples(4)
                    .create())
        {
            SynchronizedWindow glfwSynchronization = new SynchronizedWindow()
            {
                @Override
                public boolean isFocused()
                {
                    return window.isFocused();
                }

                @Override
                public void focus()
                {
                    // TODO uncomment this if it becomes possible to upgrade to new version of LWJGL that supports window focus through updated GLFW.
                    //new Thread(window::focus).start();
                }

                @Override
                public void quit()
                {
                    window.requestWindowClose();
                }
            };

            WindowSynchronization.getInstance().addListener(glfwSynchronization);

            window.addWindowCloseListener(win ->
            {
                // Cancel the window closing and let the window synchronization system close the window later if the user confirms that they want to exit.
                win.cancelWindowClose();
                WindowSynchronization.getInstance().quit();
            });

//            window.addWindowFocusGainedListener(win -> WindowSynchronization.getInstance().focusGained(glfwSynchronization));
//            window.addWindowFocusLostListener(win -> WindowSynchronization.getInstance().focusLost(glfwSynchronization));


            OpenGLContext context = window.getContext();

            // Start the request queue as soon as we have a graphics context.
            requestQueue = new IBRRequestManager<>(context);

            context.getState().enableDepthTest();

            ExtendedLightingModel lightingModel = MultithreadModels.getInstance().getLightingModel();
            EnvironmentModel environmentModel = MultithreadModels.getInstance().getEnvironmentModel();
            ExtendedCameraModel cameraModel = MultithreadModels.getInstance().getCameraModel();
            ExtendedObjectModel objectModel = MultithreadModels.getInstance().getObjectModel();
            SettingsModel settingsModel = MultithreadModels.getInstance().getSettingsModel();
            LoadingModel loadingModel = MultithreadModels.getInstance().getLoadingModel();

            // Bind tools
            ToolBindingModel toolBindingModel = new ToolBindingModelImpl();

            toolBindingModel.setDragTool(new MouseMode(0, ModifierKeys.NONE), DragToolType.ORBIT);
            toolBindingModel.setDragTool(new MouseMode(1, ModifierKeys.NONE), DragToolType.PAN);
            toolBindingModel.setDragTool(new MouseMode(2, ModifierKeys.NONE), DragToolType.PAN);
            toolBindingModel.setDragTool(new MouseMode(0, ModifierKeysBuilder.begin().alt().end()), DragToolType.TWIST);
            toolBindingModel.setDragTool(new MouseMode(1, ModifierKeysBuilder.begin().alt().end()), DragToolType.DOLLY);
            toolBindingModel.setDragTool(new MouseMode(2, ModifierKeysBuilder.begin().alt().end()), DragToolType.DOLLY);
            toolBindingModel.setDragTool(new MouseMode(0, ModifierKeysBuilder.begin().shift().end()), DragToolType.ROTATE_ENVIRONMENT);
            toolBindingModel.setDragTool(new MouseMode(1, ModifierKeysBuilder.begin().shift().end()), DragToolType.FOCAL_LENGTH);
            toolBindingModel.setDragTool(new MouseMode(2, ModifierKeysBuilder.begin().shift().end()), DragToolType.FOCAL_LENGTH);
            toolBindingModel.setDragTool(new MouseMode(1, ModifierKeysBuilder.begin().control().shift().end()), DragToolType.LOOK_AT_POINT);
            toolBindingModel.setDragTool(new MouseMode(2, ModifierKeysBuilder.begin().control().shift().end()), DragToolType.LOOK_AT_POINT);
            toolBindingModel.setDragTool(new MouseMode(0, ModifierKeysBuilder.begin().control().end()), DragToolType.OBJECT_ROTATION);
            toolBindingModel.setDragTool(new MouseMode(1, ModifierKeysBuilder.begin().control().end()), DragToolType.OBJECT_CENTER);
            toolBindingModel.setDragTool(new MouseMode(2, ModifierKeysBuilder.begin().control().end()), DragToolType.OBJECT_CENTER);
            toolBindingModel.setDragTool(new MouseMode(0, ModifierKeysBuilder.begin().control().alt().end()), DragToolType.OBJECT_TWIST);

            toolBindingModel.setKeyPressTool(new KeyPress(Key.UP, ModifierKeys.NONE), KeyPressToolType.ENVIRONMENT_BRIGHTNESS_UP_LARGE);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.DOWN, ModifierKeys.NONE), KeyPressToolType.ENVIRONMENT_BRIGHTNESS_DOWN_LARGE);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.RIGHT, ModifierKeys.NONE), KeyPressToolType.ENVIRONMENT_BRIGHTNESS_UP_SMALL);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.LEFT, ModifierKeys.NONE), KeyPressToolType.ENVIRONMENT_BRIGHTNESS_DOWN_SMALL);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.UP, ModifierKeysBuilder.begin().shift().end()), KeyPressToolType.BACKGROUND_BRIGHTNESS_UP_LARGE);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.DOWN, ModifierKeysBuilder.begin().shift().end()), KeyPressToolType.BACKGROUND_BRIGHTNESS_DOWN_LARGE);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.RIGHT, ModifierKeysBuilder.begin().shift().end()), KeyPressToolType.BACKGROUND_BRIGHTNESS_UP_SMALL);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.LEFT, ModifierKeysBuilder.begin().shift().end()), KeyPressToolType.BACKGROUND_BRIGHTNESS_DOWN_SMALL);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.L, ModifierKeys.NONE), KeyPressToolType.TOGGLE_LIGHTS);
            toolBindingModel.setKeyPressTool(new KeyPress(Key.L, ModifierKeysBuilder.begin().control().end()), KeyPressToolType.TOGGLE_LIGHT_WIDGETS);

            IBRInstanceManager<OpenGLContext> instanceManager = new IBRInstanceManager<>(context);

            SceneViewportModel sceneViewportModel = MultithreadModels.getInstance().getSceneViewportModel();

            sceneViewportModel.setSceneViewport(new SceneViewport()
            {
                @Override
                public Object getObjectAtCoordinates(double x, double y)
                {
                    if (instanceManager.getLoadedInstance() != null)
                    {
                        return instanceManager.getLoadedInstance().getSceneViewportModel().getObjectAtCoordinates(x, y);
                    }
                    else
                    {
                        return null;
                    }
                }

                @Override
                public Vector3 get3DPositionAtCoordinates(double x, double y)
                {
                    if (instanceManager.getLoadedInstance() != null)
                    {
                        return instanceManager.getLoadedInstance().getSceneViewportModel().get3DPositionAtCoordinates(x, y);
                    }
                    else
                    {
                        return Vector3.ZERO;
                    }
                }

                @Override
                public Vector3 getViewingDirection(double x, double y)
                {
                    if (instanceManager.getLoadedInstance() != null)
                    {
                        return instanceManager.getLoadedInstance().getSceneViewportModel().getViewingDirection(x, y);
                    }
                    else
                    {
                        return Vector3.ZERO;
                    }
                }

                @Override
                public Vector3 getViewportCenter()
                {
                    if (instanceManager.getLoadedInstance() != null)
                    {
                        return instanceManager.getLoadedInstance().getSceneViewportModel().getViewportCenter();
                    }
                    else
                    {
                        return Vector3.ZERO;
                    }
                }

                @Override
                public Vector2 projectPoint(Vector3 point)
                {
                    if (instanceManager.getLoadedInstance() != null)
                    {
                        return instanceManager.getLoadedInstance().getSceneViewportModel().projectPoint(point);
                    }
                    else
                    {
                        return Vector2.ZERO;
                    }
                }

                @Override
                public float getLightWidgetScale()
                {
                    if (instanceManager.getLoadedInstance() != null)
                    {
                        return instanceManager.getLoadedInstance().getSceneViewportModel().getLightWidgetScale();
                    }
                    else
                    {
                        return 1.0f;
                    }
                }
            });

            WindowBasedController windowBasedController = Builder.create()
                .setCameraModel(cameraModel)
                .setEnvironmentModel(environmentModel)
                .setLightingModel(lightingModel)
                .setObjectModel(objectModel)
                .setSettingsModel(settingsModel)
                .setToolBindingModel(toolBindingModel)
                .setSceneViewportModel(sceneViewportModel)
                .build();

            windowBasedController.addAsWindowListener(window);

            loadingModel.setLoadingHandler(instanceManager);
            
            instanceManager.setObjectModel(() -> Matrix4.IDENTITY);
            instanceManager.setCameraModel(cameraModel);
            instanceManager.setLightingModel(lightingModel);
            instanceManager.setObjectModel(objectModel);
            instanceManager.setSettingsModel(settingsModel);

            window.addKeyPressListener((win, key, modifierKeys) ->
            {
                if (key == Key.F11)
                {
                    System.out.println("reloading program...");

                    try
                    {
                        // reload program
                        instanceManager.getLoadedInstance().reloadShaders();
                    }
                    catch (RuntimeException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

            // Create a new application to run our event loop and give it the WindowImpl for polling
            // of events and the OpenGL context.  The ULFRendererList provides the renderable.
            InteractiveApplication app = InteractiveGraphics.createApplication(window, context, instanceManager);

            requestQueue.setInstanceManager(instanceManager);
            requestQueue.setLoadingMonitor(new LoadingMonitor()
            {
                @Override
                public void startLoading()
                {
                    loadingModel.getLoadingMonitor().startLoading();
                }

                @Override
                public void setMaximum(double maximum)
                {
                    loadingModel.getLoadingMonitor().setMaximum(maximum);
                }

                @Override
                public void setProgress(double progress)
                {
                    loadingModel.getLoadingMonitor().setProgress(progress);
                }

                @Override
                public void loadingComplete()
                {
                    loadingModel.getLoadingMonitor().loadingComplete();
                }

                @Override
                public void loadingFailed(Exception e)
                {
                    loadingModel.getLoadingMonitor().loadingFailed(e);
                }
            });

            app.addRefreshable(new Refreshable()
            {
                @Override
                public void initialize()
                {
                }

                @Override
                public void refresh()
                {
                    requestQueue.executeQueue();
                }

                @Override
                public void terminate()
                {
                }
            });

            window.show();

            // Keep the graphics thread paused while the window is minimized.
            app.addPollable(new EventPollable()
            {
                @Override
                public void pollEvents()
                {
                    while (stage != null && stage.isIconified() && requestQueue.isEmpty())
                    {
                        try
                        {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e)
                        {
                        }
                    }
                }

                @Override
                public boolean shouldTerminate()
                {
                    return false;
                }
            });

            // Wake the graphics thread up when the window is un-minimized.
            if (stage != null)
            {
                Thread graphicsThread = Thread.currentThread();

                stage.iconifiedProperty().addListener((observable, wasIconified, isIconified) ->
                {
                    if (wasIconified && !isIconified && requestQueue.isEmpty())
                    {
                        graphicsThread.interrupt();
                    }
                });
            }

            // Process CLI args after the main window has loaded.
            MainApplication.addStartListener(st -> processArgs(args));

            try
            {
                app.run();
            }
            catch(RuntimeException|InitializationException e)
            {
                Optional.ofNullable(loadingModel.getLoadingMonitor()).ifPresent(loadingMonitor -> loadingMonitor.loadingFailed(e));
                throw e;
            }
        }
        finally
        {
            // The event loop has terminated so cleanup the windows and exit with a successful return code.
            WindowImpl.closeAllWindows();
        }
    }

    private static void processArgs(String... args)
    {
        // Load project if requested
        if (args.length >= 1)
        {
            if (args[0].endsWith(".vset"))
            {
                File vsetFile = new File(args[0]);
                new Thread(() -> MultithreadModels.getInstance().getLoadingModel().loadFromVSETFile(vsetFile.getPath(), vsetFile)).run();
            }
            else
            {
                // Using Platform.runLater since full IBR projects include stuff that's managed by JavaFX (cameras, lights, etc.)
                Platform.runLater(() ->
                {
                    try
                    {
                        File vsetFile = MultithreadModels.getInstance().getProjectModel().openProjectFile(new File(args[0]));
                        new Thread(() -> MultithreadModels.getInstance().getLoadingModel().loadFromVSETFile(vsetFile.getPath(), vsetFile)).run();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    catch (ParserConfigurationException e)
                    {
                        e.printStackTrace();
                    }
                    catch (SAXException e)
                    {
                        e.printStackTrace();
                    }
                });
            }
        }

        // Execute command if requested, using reflection
        if (args.length >= 2)
        {
            try
            {
                Class<?> requestClass = Class.forName(args[1]);
                Method createMethod = requestClass.getDeclaredMethod("create", IBRelightModels.class, String[].class);
                if (IBRRequest.class.isAssignableFrom(createMethod.getReturnType())
                    && ((createMethod.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) == (Modifier.PUBLIC | Modifier.STATIC)))
                {
                    // Add request to the queue
                    //noinspection unchecked
                    Rendering.getRequestQueue().addIBRRequest(
                        (IBRRequest<OpenGLContext>) createMethod.invoke(null, MultithreadModels.getInstance(), args));

                    // Quit after the request finishes
                    // Use IBRRequest (rather than GraphicsRequest) so that it gets queued up after the actual request,
                    // once the project has finished loading
                    Rendering.getRequestQueue().addIBRRequest(
                        (renderable, callback) -> WindowSynchronization.getInstance().quitWithoutConfirmation());
                }
            }
            catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
            {
                e.printStackTrace();
            }
        }
    }

    private static void printSupportedImageFormats()
    {
        // Get list of all informal format names understood by the current set of registered readers
        String[] formatNames = ImageIO.getReaderFormatNames();

        Collection<String> set = Arrays.stream(formatNames)
            .map(String::toLowerCase)
            .collect(Collectors.toCollection(() -> new HashSet<>(formatNames.length)));

        System.out.println("Supported image formats: " + set);
    }
}
