#version 330

/*
 *  Copyright (c) Michael Tetzlaff 2022
 *  Copyright (c) The Regents of the University of Minnesota 2019
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

uniform int objectID;
uniform sampler2D lightTexture;
uniform vec3 color;

in vec3 fPosition;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out int fragObjectID;

void main()
{
    float intensity = texture(lightTexture, fPosition.xy / 2 + vec2(0.5))[0];

    if (intensity == 0.0)
    {
        discard;
    }
    else
    {
        fragColor = vec4(color * intensity, 1.0);
        fragObjectID = objectID;
    }
}