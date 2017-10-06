package tetzlaff.models.impl;

import java.util.Objects;

import tetzlaff.models.ReadonlySettingsModel;
import tetzlaff.models.SafeReadonlySettingsModel;

public class SafeReadonlySettingsModelImpl implements SafeReadonlySettingsModel
{
    private final ReadonlySettingsModel base;

    public SafeReadonlySettingsModelImpl(ReadonlySettingsModel base)
    {
        this.base = base;
    }

    public static Object getDefault(Class<?> settingType)
    {
        if (Objects.equals(settingType, Boolean.class))
        {
            return Boolean.FALSE;
        }
        else if (Objects.equals(settingType, Byte.class))
        {
            return (byte) 0;
        }
        else if (Objects.equals(settingType, Short.class))
        {
            return (short) 0;
        }
        else if (Objects.equals(settingType, Integer.class))
        {
            return 0;
        }
        else if (Objects.equals(settingType, Long.class))
        {
            return 0L;
        }
        else if (Objects.equals(settingType, Float.class))
        {
            return 0.0f;
        }
        else if (Objects.equals(settingType, Double.class))
        {
            return 0.0;
        }
        else
        {
            return null;
        }
    }

    @Override
    public <T> T get(String name, Class<T> settingType)
    {
        if (base.exists(name, settingType))
        {
            return base.get(name, settingType);
        }
        else
        {
            return (T)getDefault(settingType);
        }
    }
}
