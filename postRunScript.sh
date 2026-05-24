#! /bin/bash

mvn clean package
rm target/boat-rental-0.1.0.jar.original
mv target/boat-rental-0.1.0.jar Cardinal.jar
git add .
read -p "Enter your commit message: " message
git commit -m "$message"
git push

