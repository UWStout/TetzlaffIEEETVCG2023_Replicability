﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Tetzlaff.ReflectanceAcquisition.Pipeline.DataModels
{
    public interface IFrame
    {
        int Width { get; }
        int Height { get; }
    }
}
