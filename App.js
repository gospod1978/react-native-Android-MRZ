/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 * @flow strict-local
 */

 import React, { Component } from 'react';
 import ReactNative, { View, Text, Image, NativeModules, Button, PermissionsAndroid } from 'react-native'
 import MrzMy from 'react-native-mrz-my';
//  import ImgToBase64 from 'react-native-image-base64';
 
 
 import mrzReaderModule from './MrzReaderModule'
 
 
 class App extends Component {
   constructor(props) {
     super(props)
     this.state = {
       image: ''
     }
   }
 
   onPress = async () => {
     try {
       const granted = await PermissionsAndroid.request(
         PermissionsAndroid.PERMISSIONS.CAMERA,
         {
           'title': 'Cool Photo App Camera Permission',
           'message': 'Cool Photo App needs access to your camera ' +
             'so you can take awesome pictures.'
         }
       )
       if (granted === PermissionsAndroid.RESULTS.GRANTED) {
         console.log("You can use the camera")
         await mrzReaderModule.openIdBackScanner(
           (result) => {
             // let myImage = result.idImage.split(',')
             // console.log(myImage[1]);
             this.setState({ image: result.idImage });
             // console.log(`String mrz ${result.mrzValue}`);
             // console.log(`String base64 image ${result.idImage}`);
             // console.log(`String Whole value image ${result.mrzValueBirth}`);
           }
         );
 
 
 
       } else {
         console.log("Camera permission denied")
       }
     } catch (err) {
       console.warn(err)
     }
 
 
   };
 
   Clear = async () => {
     this.setState({image: ''})
   }
 
 
   render() {
     let modalData = ''
     if (this.state.image != '') {
       let imageStyle = { flex: 1, width: 350, height: 350, resizeMode: "contain" }
 
       modalData = (
         <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', alignSelf: 'center' }}>
 
               <Image source={{ uri: this.state.image}} style={imageStyle} />
           <View >
             <View>
               <Button
                 title="Cancel"
                 color="#841584"
                 onPress={this.Clear}
               />
 
             </View>
           </View>
         </View>
       )
     } else {
       modalData = (
         <View>
           <Button
             title="Click to invoke your native module!"
             color="#841584"
             onPress={this.onPress}
           />
         </View>
       )
     }
     return (
       <View style={{ flex: 0, justifyContent: 'center', alignItems: 'center', height: 700 }}>
         <Text>OUR Test</Text>
         {/* <Button
           title="Click to invoke your native module!"
           color="#841584"
           onPress={this.onPress}
         /> */}
         {modalData}
       </View>
     )
   }
 }
 
 export default App;
 