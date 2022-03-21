# to compile the program, type 'make'
all:
	javac -cp ./LIBS/commons-codec-1.15/commons-codec-1.15.jar:./LIBS/pdfbox-app-1.8.16.jar  *.java

# to execute the program to translate a file or a folder, type 'make run target='
run:
	java -cp ./LIBS/commons-codec-1.15/commons-codec-1.15.jar:./LIBS/pdfbox-app-1.8.16.jar:. Translate $(target)

# to clean the classes
clean:
	rm *.class
