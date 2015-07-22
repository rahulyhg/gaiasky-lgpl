#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

attribute vec4 a_position;
attribute vec4 a_color;
attribute vec4 a_additional;

uniform float u_pointAlphaMin;
uniform float u_pointAlphaMax;
uniform float u_fovFactor;
uniform float u_starBrightness;
uniform float u_alpha;
uniform float u_pointSize;

uniform mat4 u_projModelView;
uniform vec3 u_camPos;

varying vec4 v_col;


float lint(float x, float x0, float x1, float y0, float y1) {
    return mix(y0, y1, (x - x0) / (x1 - x0));
}

void main() {
    vec3 pos = a_position.xyz - u_camPos;
    
    float a_size = a_additional.x;
    float a_thAnglePoint = a_additional.y;
    
    float viewAngleApparent = atan((a_size * u_starBrightness) / length(pos)) / u_fovFactor;
    float opacity = pow(lint(viewAngleApparent, 0.0, a_thAnglePoint, u_pointAlphaMin, u_pointAlphaMax), 0.5);
    
    v_col = vec4(a_color.rgb, opacity * u_alpha * step(viewAngleApparent, a_thAnglePoint * 10.0));

	gl_Position = u_projModelView * vec4(pos, 0.0);
    gl_PointSize = u_pointSize;
}