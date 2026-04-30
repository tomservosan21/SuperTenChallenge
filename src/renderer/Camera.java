package renderer;

//================= Camera.java =================
public class Camera {
	
	public double angle = Math.toRadians(5); // example angle
	
 //public Vector3 pos=new Vector3(0,0,20);
	//Matrix4 rot = Matrix4.identity();
	
	public	Matrix4 rot = Matrix4.mul(
		    //Matrix4.rotationZ(Math.PI),
			Matrix4.rotationZ(0),
		    Matrix4.rotationX(Math.toRadians(90))
		);
 
	public		double startX = -2.0;
	public		 double startZ = 1.0;
 
	public		 double CELL = 4.0;
	public		 double PLAYER_HEIGHT = 2.0; // half a cube, good eye level

	public	 Vector3 pos = new Vector3(
     startX * CELL + CELL / 2,
     PLAYER_HEIGHT,
     startZ * CELL + CELL / 2
 );
 
 public Matrix4 getView(){
	  
	 Matrix4 rotTrans = Matrix4.transpose(rot);
	 Matrix4 trans =  Matrix4.translation(-pos.x,-pos.y,-pos.z);
	 Matrix4 result = Matrix4.mul(trans, rotTrans);

	 return result;
	 }    
 
 public Matrix4 getRotation(){
	 //Matrix4 trans =  Matrix4.translation(-pos.x,-pos.y,-pos.z);
	 
	 //Matrix4 tempRot = new Matrix4(rot);
	 
	 //Matrix4 newView = Matrix4.mul(trans, rot);
	 
	 return rot;
	 }    
}

