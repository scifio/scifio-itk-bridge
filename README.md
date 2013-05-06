scifio-itk-bridge
=================

Provides a simple bridge class that can be called by an ITK ImageIO (C++) to pipe information to and from SCIFIO-based Readers and Writers (Java).

Instructions for use:
This project should be automatically downloaded by ITK. However, the SCIFIOImageIO is not enabled by default in ITK. To ensure its use, set the ITK_AUTOLOAD_PATH environment variable to point to the itkSCIFIOImageIO after it's built.

NB: This is not an ITK-specific solution, and nothing in the Java code mandates the use of ITK. But as this solution uses pipes to communicate between C++ and Java, care must be taken in the formatting of this application's input and reading of the output. The itkSCIFIOImageIO in the ITK project can be used as a template on how to read data using the SCIFIOITKBridge.
