package com.bee.rubik;
public class OrientTest {
  public static void main(String[] a){
    int[] SF={2,1,5,4,0,3};
    int[][] FR={{0,0,1},{1,0,0},{0,0,-1},{-1,0,0},{0,1,0},{0,-1,0}};
    int[][] UP={{0,1,0},{0,1,0},{0,1,0},{0,1,0},{0,0,-1},{0,0,1}};
    for(int k=0;k<6;k++){ float[] M=Quat.toMatrix(Quat.orient(FR[k],UP[k]));
      for(int i=0;i<9;i++){ int[] p=CubeModel.POS[SF[k]*9+i]; int[] n=CubeModel.NRM[SF[k]*9+i];
        float x=M[0]*p[0]+M[1]*p[1]+M[2]*p[2], y=M[3]*p[0]+M[4]*p[1]+M[5]*p[2];
        float nz=M[6]*n[0]+M[7]*n[1]+M[8]*n[2];
        if(Math.round(x)!=i%3-1||Math.round(y)!=1-i/3||Math.round(nz)!=1) throw new RuntimeException("face step "+k+" cell "+i);
      }}
    System.out.println("orient OK");
  }}
