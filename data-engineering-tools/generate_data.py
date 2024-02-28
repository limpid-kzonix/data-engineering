import json
from jsonlines import jsonlines
import random
import string

def randomword(length):
   letters = string.ascii_lowercase
   return ''.join(random.choice(letters) for i in range(length))


data = []
for _ in range(1000000):
    id_string = f"random_{randomword(8)}"
    type_string = f"data_{randomword(8)}"
    
    data_item = {"id": id_string, "type": type_string, "data1": type_string, "data2": type_string, "data3": type_string, "data4": type_string, "data5": type_string, "data6": type_string, "data7": type_string, "data8": type_string, "data9": type_string, "data10": type_string, "data11": type_string, "data12": type_string, "data13": type_string, "data14": type_string, "data15": type_string, "data16": type_string, "data17": type_string, "data18": type_string, "data19": type_string, "data20": type_string, "data21": type_string, "data22": type_string, "data23": type_string, "data24": type_string, "data25": type_string, "data26": type_string, "data27": type_string, "data28": type_string, "data29": type_string, "data30": type_string, "data31": type_string, "data32": type_string, "data33": type_string, "data34": type_string, "data35": type_string, "data36": type_string, "data37": type_string, "data38": type_string, "data39": type_string, "data40": type_string, "data41": type_string, "data42": type_string, "data43": type_string, "data44": type_string, "data45": type_string, "data46": type_string, "data47": type_string, "data48": type_string, "data49": type_string, "data50": type_string, "data51": type_string, "data52": type_string, "data53": type_string, "data54": type_string, "data55": type_string, "data56": type_string, "data57": type_string, "data58": type_string, "data59": type_string, "data60": type_string, "data61": type_string, "data62": type_string, "data63": type_string, "data64": type_string, "data65": type_string, "data66": type_string, "data67": type_string, "data68": type_string, "data69": type_string, "data70": type_string, "data71": type_string, "data72": type_string, "data73": type_string, "data74": type_string, "data75": type_string, "data76": type_string, "data77": type_string}
    data.append(data_item)

with jsonlines.open("example_file.jsonl", 'w') as writer:
    for item in data:
        writer.write(item)