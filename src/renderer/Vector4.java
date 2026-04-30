package renderer;

//================= Vector4.java =================
public class Vector4 {
 public double x,y,z,w;
 public Vector4(double x,double y,double z,double w){this.x=x;this.y=y;this.z=z;this.w=w;}
 public Vector3 toVec3(){return new Vector3(x/w,y/w,z/w);}    
}
