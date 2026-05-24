#! /bin/bash

mvn clean package
cd target
rm boat-rental-0.1.0.jar.original
mv boat-rental-0.1.0.jar Cardinal.jar
cd ../
git add .
read -p "Enter your commit message: " message
git commit -m "$message"
git push

