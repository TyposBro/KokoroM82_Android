// Adopted from: https://gist.github.com/INFOTRICKS1on1/b231071c350afc499f0b3997406ca898

package com.example.kokoro82m.tts.g2p;

import java.io.*;   //importing library class "io"
class Num2Word  //creating a class of name "Num2Word"
{
    //starting of main function
    public static void main(String args[]) throws IOException
    {
        BufferedReader br=new BufferedReader(new InputStreamReader(System.in));

        //creation of array "ty" of String datatype and assigning values directly into it
        String ty[]={"","","Twenty","Thirty","Forty","Fifty","Sixty","Seventy","Eighty","Ninety"};

        //creation of array "ty" of String datatype and assigning values directly into it
        String ten[]={"","Ten","Eleven","Twelve","Thirteen","Fourteen","Fifteen","Sixteen","Seventeen",
            "Eighteen","Nineteen"};

        //creation of array "unit" of String datatype and assigning values directly into it
        String unit[]={"","One","Two","Three","Four","Five","Six","Seven","Eight","Nine"};

        System.out.print("Enter a Number : ");
        int n=Integer.parseInt(br.readLine());     //taking number as input from user

        /*checking whether the number is in the range [1-9999] or not*/
        if(n<1 || n>9999)
            System.out.println("Out of Range");

        else
        {
            int th=n/1000; //finding the digit at thousand's place
            int h=(n/100)%10; //finding the digit at hundred's place
            int t=(n/10)%10; //finding the digit at ten's place
            int u=n%10; //finding the digit at unit's place

            System.out.print("The Number in Words : ");

            /*Condition for printing digit at thousand's place, is that it should not be zero*/
            if(th!=0)
                System.out.print(unit[th]+" Thousand");

            /*Condition for printing digit at hundred's place, is that it should not be zero*/
            if(h!=0)
                System.out.print(" "+unit[h]+" Hundred");

            /*Condition for printing the word "And"*/
            if((t!=0 || u!=0)&&(th!=0 || h!=0))
                System.out.print(" And");

            /*Condition for printing digit at ten's place*/
            if(t==1) //When digit at ten's place is 1, we have different words like Ten, Eleven etc.
                System.out.print(" "+ten[u+1]);

            else //if it is not 1 then we print the words following a normal pattern
                System.out.print(" "+ty[t]+" "+unit[u]);
        }
    }  //end of main function
}   //end of class