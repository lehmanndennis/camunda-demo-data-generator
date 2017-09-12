[![Build Status](https://travis-ci.org/lehmanndennis/camunda-demo-data-generator.svg?branch=master)](https://travis-ci.org/lehmanndennis/camunda-demo-data-generator)

Camunda Demo Data Generator
===========================

This Project is supposed as extension for camunda.
It provides the ability to generate data for the camunda history tables.
It is not necessary to edit the BPMN model to use the history data generation. 
Therefore the data generation can currently only be started via webapplication.

The current master version was successfully tested on camunda 7.6.0 with H2 database on Wilfly 10.x.

# Getting Started

Build the demo-data-generator.war using Maven and deploy it to your container.
After deployment go to http://localhost:8080/demo-data-generator/. You might have to adjust the host and port depending on your system.

## Data generation

To generate data you have to specify at least the process name and version.
After that you can already generate some data with default configuration.

If you don't want to use the defaults you can look at the next step with the description of the configuration.
You will be able to configure most elements in the model itself.
Unfortunately there is currently no possibility to save the used configuration for a specific process.

## Configuration

The Configuration is separated in 2 parts. General and element specific configuration.

The general part consists of the following possibilities:
* Generate new instances only from Mo-Fr (8:00-17:00)
* End Usertasks only from Mo-Fr (8:00-17:00)
* The date when the first processinstance will be generated
* The number of instances to generate (current max is at 5000)
* The distribution for new instances
    * Mean time in seconds
    * Standard deviation in seconds

After selecting the process name and version you will see the BPMN-diagram for the selected process.
There you can configure all elements. This is the second part of the configuration:
* Add process variables with random values for
    * Tasks
    * Boundary events
    * Start and end events
* Add probabilities for
    * Boundary events
    * Sequence flows (after Gateways)
* Add a distribution for the duration of user tasks

If you want to use the random process variable generation you have to specify a name and a type for each variable.
These variables are generated as TypedValues.
The generated values for the supported types are as follows:
* BOOLEAN: true / false
* DOUBLE: 0-999
* INTEGER: 0-1000
* STRING: 1-50 alphabetic characters
Any other types are currently not supported. If you need more types or other values feel free to extend the random value generation.

## Delete generated data

The data inserted in the history tables uses the businessKey "demoDataGeneration". 
With this key in mind you should be able to identify all generated entries in the history tables.
If you want to delete all generated data with one button you can simply press "Delete generated History". 
All tables will be cleaned up by removing the entries with businessKey "demoDataGeneration".

# Maintainer

- [Dennis Lehmann](https://github.com/lehmanndennis) ([NovaTec Consulting GmbH](http://www.novatec-gmbh.de/))

# License

Apache License, Version 2.0