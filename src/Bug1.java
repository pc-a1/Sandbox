class Bug1
{
    public static void main(String[] args)
    {
	System.out.print("Entrez un nombre n =  ");
	int n = Ppl.readInt();
    	int facto = 1;
	
	for (int i=1; i<=n; i=i+1)
	    {
		
		facto = facto * i ;
	    }
	System.out.println(n+"! =  " + facto);
    }
}