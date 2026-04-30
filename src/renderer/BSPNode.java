package renderer;

//================= BSPNode.java =================
import java.util.*;

public class BSPNode {
Triangle splitter;
List<Triangle> coplanar=new ArrayList<>();
BSPNode front,back;

public BSPNode(List<Triangle> tris){
   if(tris.isEmpty()) return;
   splitter=tris.get(0);
   coplanar.add(splitter);

   List<Triangle> frontList=new ArrayList<>();
   List<Triangle> backList=new ArrayList<>();

   Vector3 n=getNormal(splitter);
   Vector3 p=splitter.v0.pos.toVec3();

   for(int i=1;i<tris.size();i++){
       Triangle t=tris.get(i);
       double d=classify(t,n,p);
       if(d>0) frontList.add(t);
       else if(d<0) backList.add(t);
       else coplanar.add(t);
   }

   if(!frontList.isEmpty()) front=new BSPNode(frontList);
   if(!backList.isEmpty()) back=new BSPNode(backList);
}

private Vector3 getNormal(Triangle t){
   return t.v1.pos.toVec3().sub(t.v0.pos.toVec3())
       .cross(t.v2.pos.toVec3().sub(t.v0.pos.toVec3())).normalize();
}

private double classify(Triangle t,Vector3 n,Vector3 p){
   double d0=t.v0.pos.toVec3().sub(p).dot(n);
   double d1=t.v1.pos.toVec3().sub(p).dot(n);
   double d2=t.v2.pos.toVec3().sub(p).dot(n);
   return (d0+d1+d2)/3.0;
}

public List<Triangle> getDrawOrder(Vector3 camPos,List<Triangle> out){
   Vector3 n=getNormal(splitter);
   Vector3 p=splitter.v0.pos.toVec3();
   double side=camPos.sub(p).dot(n);

   if(side>0){
       if(back!=null) back.getDrawOrder(camPos,out);
       out.addAll(coplanar);
       if(front!=null) front.getDrawOrder(camPos,out);
   }else{
       if(front!=null) front.getDrawOrder(camPos,out);
       out.addAll(coplanar);
       if(back!=null) back.getDrawOrder(camPos,out);
   }
   return out;
}
}
