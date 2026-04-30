package renderer;

// ================= Matrix4.java =================
public class Matrix4 {
    public double[][] m=new double[4][4];
    
    public Matrix4() {
    }
    
    public Matrix4(double[][] values) {
        if (values.length != 4 || values[0].length != 4)
            throw new IllegalArgumentException("Matrix must be 4x4");
        m = values;
    }

	public Matrix4(Matrix4 mat) {
		m=new double[4][4];
		
		m[0][0] = mat.m[0][0];
		m[0][1] = mat.m[0][1];
		m[0][2] = mat.m[0][2];
		m[0][3] = mat.m[0][3];

		m[1][0] = mat.m[1][0];
		m[1][1] = mat.m[1][1];
		m[1][2] = mat.m[1][2];
		m[1][3] = mat.m[1][3];

		m[2][0] = mat.m[2][0];
		m[2][1] = mat.m[2][1];
		m[2][2] = mat.m[2][2];
		m[2][3] = mat.m[2][3];
		
		m[3][0] = mat.m[3][0];
		m[3][1] = mat.m[3][1];
		m[3][2] = mat.m[3][2];
		m[3][3] = mat.m[3][3];
	}

	public static Matrix4 identity(){Matrix4 r=new Matrix4();for(int i=0;i<4;i++)r.m[i][i]=1;return r;}

    public static Matrix4 perspective(double fov,double aspect,double near,double far){
        Matrix4 r=new Matrix4();
        double f=1.0/Math.tan(fov/2);
        r.m[0][0]=f/aspect;
        r.m[1][1]=f;
        r.m[2][2]=(far+near)/(near-far);
        r.m[2][3]=(2*far*near)/(near-far);
        r.m[3][2]=-1;
        return r;
    }

    public static Matrix4 translation(double x,double y,double z){
        Matrix4 r=identity(); r.m[0][3]=x; r.m[1][3]=y; r.m[2][3]=z; return r;
    }
   
    public static Matrix4 transpose(Matrix4 mat){
    	
    	Matrix4 result = new Matrix4();
    	
    	result.m[0][0] = mat.m[0][0];
    	result.m[0][1] = mat.m[1][0];
    	result.m[0][2] = mat.m[2][0];
    	result.m[0][3] = mat.m[3][0];

    	result.m[1][0] = mat.m[0][1];
    	result.m[1][1] = mat.m[1][1];
    	result.m[1][2] = mat.m[2][1];
    	result.m[1][3] = mat.m[3][1];

    	result.m[2][0] = mat.m[0][2];
    	result.m[2][1] = mat.m[1][2];
    	result.m[2][2] = mat.m[2][2];
    	result.m[2][3] = mat.m[3][2];
		
    	result.m[3][0] = mat.m[0][3];
    	result.m[3][1] = mat.m[1][3];
    	result.m[3][2] = mat.m[2][3];
    	result.m[3][3] = mat.m[3][3];
    	
    	
    	return result;
    }

    public static Matrix4 mul(Matrix4 a,Matrix4 b){
        Matrix4 r=new Matrix4();
        for(int i=0;i<4;i++)for(int j=0;j<4;j++)for(int k=0;k<4;k++)
            r.m[i][j]+=a.m[i][k]*b.m[k][j];
        return r;
    }
    
 // ---------- FACTORY: ROTATION ABOUT X ----------
    public static Matrix4 rotationX(double theta) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);

        return new Matrix4(new double[][] {
            {1, 0, 0, 0},
            {0, c,-s, 0},
            {0, s, c, 0},
            {0, 0, 0, 1}
        });
    }

    // ---------- FACTORY: ROTATION ABOUT Y ----------
    public static Matrix4 rotationY(double theta) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);

        return new Matrix4(new double[][] {
            { c, 0, s, 0},
            { 0, 1, 0, 0},
            {-s, 0, c, 0},
            { 0, 0, 0, 1}
        });
    }

    // ---------- FACTORY: ROTATION ABOUT Z ----------
    public static Matrix4 rotationZ(double theta) {
        double c = Math.cos(theta);
        double s = Math.sin(theta);

        return new Matrix4(new double[][] {
            {c,-s, 0, 0},
            {s, c, 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        });
    }

    public Vector4 transform(Vector3 v){
        double[] r=new double[4]; double[] vec={v.x,v.y,v.z,1};
        for(int i=0;i<4;i++)for(int j=0;j<4;j++)r[i]+=m[i][j]*vec[j];
        return new Vector4(r[0],r[1],r[2],r[3]);
    }
}
