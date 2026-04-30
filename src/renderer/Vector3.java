package renderer;

//================= Vector3.java =================
public class Vector3 {
 public double x,y,z;
 public Vector3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
 public Vector3 add(Vector3 v){return new Vector3(x+v.x,y+v.y,z+v.z);}    
 public Vector3 sub(Vector3 v){return new Vector3(x-v.x,y-v.y,z-v.z);}    
 public Vector3 mul(double s){return new Vector3(x*s,y*s,z*s);}    
 public double dot(Vector3 v){return x*v.x+y*v.y+z*v.z;}    
 public Vector3 cross(Vector3 v){return new Vector3(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x);}    
 public Vector3 normalize() {
	    double l = Math.sqrt(x*x + y*y + z*z);
	    if (l == 0) return new Vector3(0, 0, 0);
	    return new Vector3(x / l, y / l, z / l);
	}
 
 public static Vector3 subtract(Point3D a, Point3D b) {
     return new Vector3(a.x - b.x, a.y - b.y, a.z - b.z);
 }
 
 public Vector3 neg() {
     return new Vector3(-x, -y, -z);
 }
 
 public double length() {
	    return Math.sqrt(x * x + y * y + z * z);
	}
}
