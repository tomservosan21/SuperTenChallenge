package renderer;

public class NormalCalculator {

    // Compute the surface normal of triangle ABC
    //public static Vector3 computeNormal(Vector3 A, Vector3 B, Vector3 C) {
     //   Vector3 edge1 = B.sub(A);
     //   Vector3 edge2 = C.sub(A);
     //   return edge1.cross(edge2).normalize();
    //}
    
 // Compute the surface normal of triangle ABC
    public static Vector3 computeNormal(Triangle t) {
        //Vector3 edge1 = B.sub(A);
        //Vector3 edge2 = C.sub(A);
        
        Vector3 p0=t.v0.pos.toVec3();
        Vector3 p1=t.v1.pos.toVec3();
        Vector3 p2=t.v2.pos.toVec3();
        
        Vector3 edge1 = p1.sub(p0);
        Vector3 edge2 = p2.sub(p0);
    	
        return edge1.cross(edge2).normalize();
    }
}
