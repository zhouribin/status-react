#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import "Statusgo/Statusgo.h"
#import "RCTLog.h"

@interface Status : NSObject <RCTBridgeModule, StatusgoSignalHandler>
+ (void)signalEvent:(const char *)signal;
+ (void)jailEvent:(NSString *)chatId
             data:(NSString *)data;
+ (BOOL)JSCEnabled;
+ (void)handleSignal:(NSString *)signal;

@end
